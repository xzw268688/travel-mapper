package com.travelmapper.service.itinerary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.controller.itinerary.dto.GenerateItineraryRequest;
import com.travelmapper.controller.itinerary.dto.GenerateItineraryResponse;
import com.travelmapper.repository.entity.Country;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.travelmapper.service.catalog.CatalogService;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import com.travelmapper.service.llm.LlmItineraryRequest;
import com.travelmapper.service.llm.LlmProviderRegistry;
import com.travelmapper.service.llm.LlmTimeoutException;
import com.travelmapper.service.quota.QuotaDeduction;
import com.travelmapper.service.quota.QuotaService;
import com.travelmapper.service.quota.QuotaSummary;
import com.travelmapper.service.search.GuideLink;
import com.travelmapper.service.search.GuideLinkSanitizer;
import com.travelmapper.service.search.GuideSearchQuery;
import com.travelmapper.service.search.SearchException;
import com.travelmapper.service.search.SearchProviderHolder;
import com.travelmapper.service.search.SearchTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class ItineraryGenerationService {

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.BASIC_ISO_DATE;

    private final CatalogService catalogService;
    private final QuotaService quotaService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final SearchProviderHolder searchProviderHolder;
    private final GuideLinkSanitizer guideLinkSanitizer;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ConfigService configService;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;

    public ItineraryGenerationService(CatalogService catalogService, QuotaService quotaService,
                                      LlmProviderRegistry llmProviderRegistry,
                                      SearchProviderHolder searchProviderHolder,
                                      GuideLinkSanitizer guideLinkSanitizer,
                                      StringRedisTemplate redis, ObjectMapper objectMapper,
                                      ConfigService configService, UserMapper userMapper,
                                      AuditLogService auditLogService, TimeProvider timeProvider) {
        this.catalogService = catalogService;
        this.quotaService = quotaService;
        this.llmProviderRegistry = llmProviderRegistry;
        this.searchProviderHolder = searchProviderHolder;
        this.guideLinkSanitizer = guideLinkSanitizer;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
        this.timeProvider = timeProvider;
    }

    public GenerateItineraryResponse generate(Long userId, GenerateItineraryRequest request) {
        NormalizedRequest normalized = normalize(request);
        quotaService.assertAvailable(userId);

        String fingerprint = fingerprint(normalized.countryCode(), normalized.cityCodes(),
                normalized.days(), normalized.preference());
        String resultKey = "quota:dedupe:" + userId + ":" + fingerprint;
        String cached = redis.opsForValue().get(resultKey);
        if (cached != null) {
            try {
                CachedGeneration generation = objectMapper.readValue(cached, CachedGeneration.class);
                GenerateItineraryResponse response =
                        buildResponse(normalized.requestId(), generation, true, userId);
                auditLogService.record(userId, "ITINERARY_GENERATE", "ITINERARY", null,
                        Map.of("mode", "DRAFT", "dayCount", response.days().size(),
                                "deduplicated", true), null);
                return response;
            } catch (Exception ex) {
                redis.delete(resultKey);
            }
        }

        String lockKey = "quota:dedupe:lock:" + userId + ":" + fingerprint;
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(120));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "相同行程正在生成中，请稍候");
        }

        QuotaDeduction deduction;
        try {
            deduction = quotaService.deduct(userId, normalized.requestId());
            CompletableFuture<List<GuideLink>> guidesFuture = CompletableFuture.supplyAsync(
                    () -> loadGuides(userId, normalized));

            GeneratedPlan plan;
            try {
                plan = generatePlanWithRetry(normalized);
            } catch (RuntimeException ex) {
                quotaService.refund(userId, normalized.requestId());
                throw ex;
            }

            List<GuideLink> guides;
            try {
                guides = guidesFuture.get(6, TimeUnit.SECONDS);
            } catch (Exception ex) {
                guides = List.of();
            }
            if (guides == null) {
                guides = List.of();
            }

            GenerateItineraryResponse response = new GenerateItineraryResponse(
                    normalized.requestId(),
                    normalized.countryCode(),
                    normalized.startDate(),
                    normalized.startDate().plusDays(normalized.days() - 1L),
                    plan.days(),
                    guides,
                    plan.preparations(),
                    plan.warnings(),
                    false,
                    new GenerateItineraryResponse.QuotaInfo(
                            deduction.source(), deduction.freeUsedToday(),
                            deduction.freeLimit(), deduction.packBalance()));

            CachedGeneration cachedGeneration = new CachedGeneration(
                    response.countryCode(), response.startDate(), response.endDate(),
                    response.days(), response.guides(), response.preparations(), response.warnings());
            try {
                redis.opsForValue().set(resultKey,
                        objectMapper.writeValueAsString(cachedGeneration), Duration.ofSeconds(60));
            } catch (Exception ex) {
                log.warn("failed to cache generation dedupe result", ex);
            }
            auditLogService.record(userId, "ITINERARY_GENERATE", "ITINERARY", null,
                    Map.of("mode", "DRAFT", "dayCount", response.days().size(),
                            "deduplicated", false), null);
            return response;
        } finally {
            redis.delete(lockKey);
        }
    }

    private GenerateItineraryResponse buildResponse(String requestId, CachedGeneration generation,
                                                    boolean deduplicated, Long userId) {
        QuotaSummary summary = quotaService.getSummary(userId);
        String source = "VIP".equals(summary.plan()) ? "VIP"
                : summary.packBalance() > 0 ? "PACK" : "FREE";
        return new GenerateItineraryResponse(
                requestId,
                generation.countryCode(),
                generation.startDate(),
                generation.endDate(),
                generation.days(),
                generation.guides(),
                generation.preparations(),
                generation.warnings(),
                deduplicated,
                new GenerateItineraryResponse.QuotaInfo(
                        source, summary.freeUsedToday(), summary.freeLimit(), summary.packBalance()));
    }

    private NormalizedRequest normalize(GenerateItineraryRequest request) {
        try {
            UUID.fromString(request.getRequestId());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "requestId 必须为 UUID");
        }
        String countryCode = request.getCountryCode().toUpperCase(Locale.ROOT);
        Country country = catalogService.requireEnabledCountry(countryCode);
        if (request.getStartDate().isBefore(timeProvider.today().minusDays(1))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "开始日期不得早于昨天");
        }
        LinkedHashSet<String> cities = new LinkedHashSet<>();
        for (String raw : request.getCityCodes()) {
            String cityCode = raw.toUpperCase(Locale.ROOT);
            catalogService.requireEnabledCity(countryCode, cityCode);
            cities.add(cityCode);
        }
        if (cities.isEmpty() || cities.size() > 5) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "城市数量必须为 1-5 个");
        }
        List<String> cityCodes = new ArrayList<>(cities);
        List<String> cityNames = cityCodes.stream()
                .map(code -> catalogService.cityName(countryCode, code))
                .toList();
        String preference = request.getPreference() == null ? "" : request.getPreference().trim();
        return new NormalizedRequest(request.getRequestId(), countryCode, country.getNameZh(),
                cityCodes, cityNames, request.getDays(), request.getStartDate(), preference);
    }

    /**
     * Used by regeneration and any caller that already owns the quota/transaction
     * boundary. No quota is deducted here.
     */
    public GeneratedPlan generatePlan(String rawCountryCode, List<String> rawCityCodes,
                                      int days, LocalDate startDate, String preference) {
        String countryCode = rawCountryCode.toUpperCase(Locale.ROOT);
        String countryName = catalogService.countryName(countryCode);
        List<String> cityCodes = rawCityCodes.stream()
                .map(code -> code.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        List<String> cityNames = cityCodes.stream()
                .map(code -> catalogService.cityName(countryCode, code))
                .toList();
        NormalizedRequest normalized = new NormalizedRequest(
                UUID.randomUUID().toString(), countryCode, countryName,
                cityCodes, cityNames, days, startDate,
                preference == null ? "" : preference.trim());
        return generatePlanWithRetry(normalized);
    }

    private GeneratedPlan generatePlanWithRetry(NormalizedRequest request) {
        LlmItineraryRequest llmRequest = new LlmItineraryRequest(
                request.countryCode(), request.countryName(), request.cityCodes(),
                request.cityNames(), request.days(), request.startDate(), request.preference());
        // 整次生成共用一个预算：超时不重试，避免把用户等待时间翻倍。
        long deadline = System.currentTimeMillis()
                + (configService.getInt(ConfigKeys.LLM_TIMEOUT_SECONDS, 30) + 10L) * 1000L;
        // 重试次数来自后台配置；受整体时间预算约束，最快也只可能多试几次。
        int retries = Math.max(0, Math.min(3, configService.getInt(ConfigKeys.LLM_MAX_RETRY, 1)));
        int attempts = 1 + retries;
        RuntimeException last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (remainingMs(deadline) < 5_000) {
                break;
            }
            long startedAt = System.currentTimeMillis();
            try {
                GeneratedPlan plan = parsePlan(callLlm(llmRequest, deadline), llmRequest);
                log.info("itinerary generation attempt {} succeeded in {}ms", attempt + 1,
                        System.currentTimeMillis() - startedAt);
                return plan;
            } catch (LlmTimeoutException ex) {
                log.warn("itinerary generation attempt {} timed out after {}ms, no retry",
                        attempt + 1, System.currentTimeMillis() - startedAt);
                throw ex;
            } catch (InvalidPlanException | BusinessException ex) {
                log.warn("itinerary generation attempt {} failed in {}ms: {}",
                        attempt + 1, System.currentTimeMillis() - startedAt, ex.getMessage());
                last = ex;
            }
        }
        if (request.cityCodes().size() > 1 && remainingMs(deadline) >= 5_000) {
            String firstCode = request.cityCodes().get(0);
            String firstName = request.cityNames().get(0);
            LlmItineraryRequest single = new LlmItineraryRequest(
                    request.countryCode(), request.countryName(),
                    List.of(firstCode), List.of(firstName),
                    request.days(), request.startDate(), request.preference());
            for (int attempt = 0; attempt < attempts; attempt++) {
                if (remainingMs(deadline) < 5_000) {
                    break;
                }
                try {
                    GeneratedPlan plan = parsePlan(callLlm(single, deadline), single);
                    List<String> warnings = new ArrayList<>(plan.warnings());
                    warnings.add("多城市生成不稳定，已按单城市（" + firstName
                            + "）生成，可重新调整或分城市生成");
                    return new GeneratedPlan(plan.days(), plan.preparations(), warnings);
                } catch (LlmTimeoutException ex) {
                    throw ex;
                } catch (InvalidPlanException | BusinessException ex) {
                    last = ex;
                }
            }
        }
        if (last instanceof BusinessException business) {
            throw business;
        }
        throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }

    private long remainingMs(long deadline) {
        return deadline - System.currentTimeMillis();
    }

    private String callLlm(LlmItineraryRequest request, long deadline) {
        long remaining = remainingMs(deadline);
        if (remaining < 5_000) {
            throw new LlmTimeoutException();
        }
        try {
            return CompletableFuture.supplyAsync(
                            () -> llmProviderRegistry.get().generateItinerary(request))
                    .get(Math.min(remaining, (configService.getInt(ConfigKeys.LLM_TIMEOUT_SECONDS, 30) + 10L) * 1000L),
                            TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new LlmTimeoutException();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof BusinessException business) {
                throw business;
            }
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private GeneratedPlan parsePlan(String raw, LlmItineraryRequest request) {
        try {
            String cleaned = stripCodeFence(raw);
            JsonNode root = objectMapper.readTree(cleaned);
            if (!request.countryCode().equals(root.path("countryCode").asText())) {
                throw new InvalidPlanException("countryCode mismatch");
            }
            JsonNode daysNode = root.path("days");
            if (!daysNode.isArray() || daysNode.size() != request.days()) {
                throw new InvalidPlanException("day count mismatch");
            }
            Set<String> selected = new LinkedHashSet<>(request.cityCodes());
            Set<String> assigned = new LinkedHashSet<>();
            List<GenerateItineraryResponse.DayPlan> days = new ArrayList<>();
            for (int i = 0; i < daysNode.size(); i++) {
                JsonNode day = daysNode.get(i);
                if (day.path("dayIndex").asInt() != i + 1) {
                    throw new InvalidPlanException("dayIndex not continuous");
                }
                String cityCode = day.path("cityCode").asText().toUpperCase(Locale.ROOT);
                if (!selected.contains(cityCode)) {
                    throw new InvalidPlanException("city outside selection");
                }
                assigned.add(cityCode);
                JsonNode spotsNode = day.path("spots");
                if (!spotsNode.isArray() || spotsNode.size() < 1 || spotsNode.size() > 6) {
                    throw new InvalidPlanException("spot count invalid");
                }
                List<GenerateItineraryResponse.SpotPlan> spots = new ArrayList<>();
                for (JsonNode spot : spotsNode) {
                    String name = spot.path("name").asText("").trim();
                    if (name.isEmpty()) {
                        throw new InvalidPlanException("spot name missing");
                    }
                    spots.add(new GenerateItineraryResponse.SpotPlan(
                            name,
                            safeText(spot, "description", 200),
                            safeText(spot, "suggestedDuration", 20),
                            safeText(spot, "category", 32),
                            safeText(spot, "ticketPrice", 64),
                            safeText(spot, "highlights", 255),
                            safeText(spot, "bestTime", 64),
                            safeText(spot, "nearestStation", 128),
                            safeBoolean(spot, "reservationRequired"),
                            safeText(spot, "area", 128),
                            safeText(spot, "warningNotes", 255),
                            "AI"));
                }
                days.add(new GenerateItineraryResponse.DayPlan(
                        i + 1,
                        request.startDate().plusDays(i),
                        cityCode,
                        catalogService.cityName(request.countryCode(), cityCode),
                        spots));
            }
            List<String> warnings = new ArrayList<>();
            for (String city : selected) {
                if (!assigned.contains(city)) {
                    warnings.add("城市「" + catalogService.cityName(request.countryCode(), city)
                            + "」未被安排，可手动调整或重新生成");
                }
            }
            // 行前准备已迁移到国家攻略能力，行程生成不再解析或缓存该字段。
            return new GeneratedPlan(days, emptyPreparations(), warnings);
        } catch (InvalidPlanException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidPlanException(ex.getMessage());
        }
    }

    private GenerateItineraryResponse.Preparations emptyPreparations() {
        return new GenerateItineraryResponse.Preparations("", "", "", "", List.of());
    }

    /** 扩展字段一律可降级：非文本取空，超长截断，不因单字段异常让整份行程重试。 */
    static String safeText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) {
            return "";
        }
        String text = value.asText().trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    static boolean safeBoolean(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asInt() != 0;
        }
        if (value.isTextual()) {
            String text = value.asText().trim().toLowerCase(Locale.ROOT);
            return switch (text) {
                case "true", "yes", "y", "1", "是", "需要", "需预约", "需要预约", "必须预约" -> true;
                default -> false;
            };
        }
        return false;
    }

    private List<GuideLink> loadGuides(Long userId, NormalizedRequest request) {
        String normalizedPreference = normalizePreference(request.preference());
        String key = "guides:" + request.countryCode() + ":" + sha256(
                String.join(",", request.cityCodes()) + "|" + request.days() + "|" + normalizedPreference);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                List<GuideLink> links = objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, GuideLink.class));
                return guideLinkSanitizer.sanitize(links);
            }
        } catch (Exception ex) {
            redis.delete(key);
        }

        int limit = configService.getInt(ConfigKeys.SEARCH_DAILY_LIMIT, 50);
        String dailyKey = "search:daily:" + userId + ":"
                + LocalDate.now(BEIJING).format(DATE_KEY);
        Long count = redis.opsForValue().increment(dailyKey);
        if (count != null && count == 1) {
            redis.expire(dailyKey, Duration.ofDays(1));
        }
        if (count != null && count > limit) {
            return List.of();
        }

        try {
            GuideSearchQuery query = new GuideSearchQuery(
                    request.countryCode(), request.countryName(), request.cityCodes(),
                    request.cityNames(), request.days(), normalizedPreference);
            List<GuideLink> links = guideLinkSanitizer.sanitize(searchProviderHolder.search(query));
            try {
                redis.opsForValue().set(key, objectMapper.writeValueAsString(links), Duration.ofHours(24));
            } catch (Exception ex) {
                log.warn("failed to cache guides", ex);
            }
            return links;
        } catch (SearchTimeoutException ex) {
            log.info("guide search timed out; degrading to empty guides");
            return List.of();
        } catch (SearchException ex) {
            log.info("guide search failed; degrading to empty guides");
            return List.of();
        }
    }

    public static String normalizePreference(String preference) {
        if (preference == null) {
            return "";
        }
        String text = preference.toLowerCase(Locale.ROOT).trim().replaceAll("\\p{P}", " ");
        TreeSet<String> tokens = new TreeSet<>();
        for (String token : text.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        String joined = String.join(" ", tokens);
        return joined.length() > 100 ? joined.substring(0, 100) : joined;
    }

    public static String fingerprint(String countryCode, List<String> cityCodes, int days,
                                     String preference) {
        List<String> sorted = new ArrayList<>(cityCodes);
        sorted.sort(String::compareTo);
        return sha256(countryCode + "|" + String.join(",", sorted) + "|" + days + "|"
                + normalizePreference(preference));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String stripCodeFence(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            text = firstNewline >= 0 ? text.substring(firstNewline + 1) : text;
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text.trim();
    }

    
    private record NormalizedRequest(
            String requestId,
            String countryCode,
            String countryName,
            List<String> cityCodes,
            List<String> cityNames,
            int days,
            LocalDate startDate,
            String preference) {
    }

    private static class InvalidPlanException extends RuntimeException {
        InvalidPlanException(String message) {
            super(message);
        }
    }
}
