package com.travelmapper.service.ai;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.controller.ai.dto.PolishRequest;
import com.travelmapper.repository.entity.TravelNote;
import com.travelmapper.repository.mapper.TravelNoteMapper;
import com.travelmapper.service.llm.LlmProviderRegistry;
import com.travelmapper.service.quota.QuotaDeduction;
import com.travelmapper.service.quota.QuotaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AiPolishService {

    private static final Set<String> STYLES = Set.of("vivid", "concise", "elegant");
    private static final Set<String> TARGETS = Set.of("selected", "full");

    private final QuotaService quotaService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final TravelNoteMapper travelNoteMapper;
    private final StringRedisTemplate redis;

    public AiPolishService(QuotaService quotaService, LlmProviderRegistry llmProviderRegistry,
                           TravelNoteMapper travelNoteMapper, StringRedisTemplate redis) {
        this.quotaService = quotaService;
        this.llmProviderRegistry = llmProviderRegistry;
        this.travelNoteMapper = travelNoteMapper;
        this.redis = redis;
    }

    public SseEmitter stream(Long userId, PolishRequest request) {
        validate(userId, request);
        QuotaDeduction deduction = quotaService.deduct(userId, request.getRequestId());
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean deltaSent = new AtomicBoolean(false);
        AtomicInteger totalChars = new AtomicInteger();

        emitter.onTimeout(() -> {
            if (!deltaSent.get()) {
                quotaService.refund(userId, request.getRequestId());
            }
            emitter.complete();
        });
        emitter.onError(error -> {
            if (!deltaSent.get()) {
                quotaService.refund(userId, request.getRequestId());
            }
        });

        try {
            emitter.send(SseEmitter.event().name("meta")
                    .data(Map.of("requestId", request.getRequestId(),
                            "quotaSource", deduction.source())));
        } catch (IOException ex) {
            quotaService.refund(userId, request.getRequestId());
            emitter.completeWithError(ex);
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            String style = request.getStyle() == null ? "vivid" : request.getStyle();
            String target = request.getTarget() == null ? "selected" : request.getTarget();
            try {
                llmProviderRegistry.get().polishStream(request.getText(), style, target,
                        delta -> {
                            if (cancelled.get() || isCancelled(userId, request.getRequestId())) {
                                cancelled.set(true);
                                return;
                            }
                            try {
                                emitter.send(SseEmitter.event().name("delta")
                                        .data(Map.of("delta", delta)));
                                totalChars.addAndGet(delta.length());
                                deltaSent.set(true);
                            } catch (IOException ex) {
                                cancelled.set(true);
                            }
                        },
                        () -> cancelled.get() || isCancelled(userId, request.getRequestId()));
                if (cancelled.get() || isCancelled(userId, request.getRequestId())) {
                    emitter.send(SseEmitter.event().name("cancelled")
                            .data(Map.of("requestId", request.getRequestId())));
                } else {
                    emitter.send(SseEmitter.event().name("done")
                            .data(Map.of("requestId", request.getRequestId(),
                                    "totalChars", totalChars.get())));
                }
                emitter.complete();
            } catch (Exception ex) {
                boolean refunded = !deltaSent.get();
                if (refunded) {
                    quotaService.refund(userId, request.getRequestId());
                }
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
            }
        });
        return emitter;
    }

    private void validate(Long userId, PolishRequest request) {
        if (request.getText() == null || request.getText().isBlank()
                || request.getText().length() > 5000) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文本长度必须为 1-5000 字");
        }
        if (request.getStyle() != null && !STYLES.contains(request.getStyle())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "style 非法");
        }
        if (request.getTarget() != null && !TARGETS.contains(request.getTarget())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "target 非法");
        }
        if (request.getNoteId() != null) {
            TravelNote note = travelNoteMapper.selectById(request.getNoteId());
            if (note == null || !userId.equals(note.getUserId())) {
                throw new BusinessException(ErrorCode.NOTE_NOT_FOUND);
            }
        }
    }

    private boolean isCancelled(Long userId, String requestId) {
        return Boolean.TRUE.equals(redis.hasKey("cancel:" + userId + ":" + requestId));
    }
}
