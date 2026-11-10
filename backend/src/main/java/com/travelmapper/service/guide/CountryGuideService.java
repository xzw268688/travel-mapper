package com.travelmapper.service.guide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.controller.guide.dto.CountryGuideDtos;
import com.travelmapper.repository.entity.Country;
import com.travelmapper.service.catalog.CatalogService;
import com.travelmapper.service.llm.LlmProviderRegistry;
import com.travelmapper.service.quota.QuotaDeduction;
import com.travelmapper.service.quota.QuotaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class CountryGuideService {

    static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(180);
    private static final long EMITTER_TIMEOUT_MS = 130_000L;

    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] "
                    + "then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final CatalogService catalogService;
    private final QuotaService quotaService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final CountryGuideParser parser;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public CountryGuideService(CatalogService catalogService, QuotaService quotaService,
                               LlmProviderRegistry llmProviderRegistry, CountryGuideParser parser,
                               StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.quotaService = quotaService;
        this.llmProviderRegistry = llmProviderRegistry;
        this.parser = parser;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public CountryGuideDtos.CacheView cached(String rawCountryCode) {
        String countryCode = normalizeCountry(rawCountryCode);
        catalogService.requireEnabledCountry(countryCode);
        CountryGuideDtos.View guide = readCache(countryCode);
        return new CountryGuideDtos.CacheView(guide != null,
                guide == null ? null : guide.withCached(true));
    }

    public SseEmitter stream(Long userId, String requestId, String rawCountryCode) {
        validateRequestId(requestId);
        String countryCode = normalizeCountry(rawCountryCode);
        Country country = catalogService.requireEnabledCountry(countryCode);

        CountryGuideDtos.View cached = readCache(countryCode);
        if (cached != null) {
            return emitCached(requestId, cached);
        }

        String lockKey = lockKey(countryCode);
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, requestId, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            CountryGuideDtos.View latest = readCache(countryCode);
            if (latest != null) {
                return emitCached(requestId, latest);
            }
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "该国攻略正在生成，请稍后重试");
        }

        // 双重检查：拿锁期间可能已有其他实例写入缓存，命中时无需扣费。
        CountryGuideDtos.View filledWhileWaiting = readCache(countryCode);
        if (filledWhileWaiting != null) {
            releaseLock(lockKey, requestId);
            return emitCached(requestId, filledWhileWaiting);
        }

        QuotaDeduction deduction;
        try {
            deduction = quotaService.deduct(userId, requestId);
        } catch (RuntimeException ex) {
            releaseLock(lockKey, requestId);
            throw ex;
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean deltaSent = new AtomicBoolean(false);
        StringBuilder collected = new StringBuilder();

        emitter.onTimeout(() -> {
            cancelled.set(true);
            refundIfUnstarted(userId, requestId, deltaSent);
            emitter.complete();
        });
        emitter.onError(error -> {
            cancelled.set(true);
            refundIfUnstarted(userId, requestId, deltaSent);
        });

        try {
            emitter.send(SseEmitter.event().name("meta")
                    .data(Map.of("requestId", requestId,
                            "quotaSource", deduction.source(),
                            "cached", false)));
        } catch (IOException ex) {
            refundIfUnstarted(userId, requestId, deltaSent);
            releaseLock(lockKey, requestId);
            emitter.completeWithError(ex);
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                llmProviderRegistry.get().guideStream(country.getNameZh(), countryCode, delta -> {
                    if (cancelled.get()) {
                        return;
                    }
                    try {
                        emitter.send(SseEmitter.event().name("delta")
                                .data(Map.of("delta", delta)));
                        collected.append(delta);
                        deltaSent.set(true);
                    } catch (IOException ex) {
                        cancelled.set(true);
                    }
                }, cancelled::get);

                if (cancelled.get()) {
                    refundIfUnstarted(userId, requestId, deltaSent);
                    emitter.complete();
                    return;
                }

                CountryGuideDtos.View guide = parseGuide(countryCode, country.getNameZh(), collected.toString());
                writeCache(guide);
                emitter.send(SseEmitter.event().name("done")
                        .data(Map.of("requestId", requestId,
                                "cached", false,
                                "guide", guide)));
                emitter.complete();
            } catch (Exception ex) {
                boolean refunded = refundIfUnstarted(userId, requestId, deltaSent);
                log.warn("country guide generation failed country={} refunded={}",
                        countryCode, refunded, ex);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("code", ErrorCode.AI_UNAVAILABLE.getCode(),
                                    "message", refunded
                                            ? "AI 服务不可用，本次额度已退回"
                                            : "AI 服务不可用")));
                } catch (IOException ignored) {
                    // client already gone
                }
                emitter.complete();
            } finally {
                releaseLock(lockKey, requestId);
            }
        });
        return emitter;
    }

    private CountryGuideDtos.View parseGuide(String countryCode, String countryName, String raw) {
        CountryGuideParser.Result parsed = parser.parse(raw);
        if (parsed.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "攻略内容为空");
        }
        return new CountryGuideDtos.View(countryCode, countryName, parsed.intro(),
                parsed.attractions(), parsed.notice(), false);
    }

    private SseEmitter emitCached(String requestId, CountryGuideDtos.View cached) {
        CountryGuideDtos.View view = cached.withCached(true);
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        try {
            emitter.send(SseEmitter.event().name("meta")
                    .data(Map.of("requestId", requestId, "quotaSource", "CACHE", "cached", true)));
            emitter.send(SseEmitter.event().name("done")
                    .data(Map.of("requestId", requestId, "cached", true, "guide", view)));
            emitter.complete();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private CountryGuideDtos.View readCache(String countryCode) {
        String key = cacheKey(countryCode);
        try {
            String value = redis.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return objectMapper.readValue(value, CountryGuideDtos.View.class);
        } catch (Exception ex) {
            redis.delete(key);
            return null;
        }
    }

    private void writeCache(CountryGuideDtos.View guide) {
        try {
            redis.opsForValue().set(cacheKey(guide.countryCode()),
                    objectMapper.writeValueAsString(guide), CACHE_TTL);
        } catch (Exception ex) {
            log.warn("failed to cache country guide {}", guide.countryCode(), ex);
        }
    }

    /** 未输出任何 delta 时退额度；返回是否执行了退回。 */
    private boolean refundIfUnstarted(Long userId, String requestId, AtomicBoolean deltaSent) {
        if (deltaSent.get()) {
            return false;
        }
        quotaService.refund(userId, requestId);
        return true;
    }

    private void releaseLock(String lockKey, String requestId) {
        try {
            redis.execute(RELEASE_LOCK, List.of(lockKey), requestId);
        } catch (Exception ex) {
            log.debug("failed to release country guide lock {}", lockKey, ex);
        }
    }

    private String normalizeCountry(String rawCountryCode) {
        if (rawCountryCode == null || !rawCountryCode.matches("[A-Za-z0-9]{2}")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "countryCode 必须为两位字母或数字");
        }
        return rawCountryCode.toUpperCase(Locale.ROOT);
    }

    private void validateRequestId(String requestId) {
        try {
            UUID.fromString(requestId);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "requestId 必须为 UUID");
        }
    }

    private String cacheKey(String countryCode) {
        return "country-guide:v1:" + countryCode;
    }

    private String lockKey(String countryCode) {
        return "country-guide:lock:" + countryCode;
    }
}