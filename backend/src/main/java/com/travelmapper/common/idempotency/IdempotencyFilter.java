package com.travelmapper.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.security.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final int MAX_CACHE_BYTES = 1_048_576;
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> SELF_MANAGED_PATHS = Set.of("/api/card/redeem");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (!WRITE_METHODS.contains(request.getMethod())
                || idempotencyKey == null || idempotencyKey.isBlank()
                || SELF_MANAGED_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        if (!idempotencyKey.matches("[A-Za-z0-9._-]{1,128}")) {
            writeError(response, ErrorCode.PARAM_INVALID);
            return;
        }

        Long userId = RequestContext.userIdOrNull();
        String identity = userId == null ? "anonymous" : userId.toString();
        String baseKey = "idem:" + identity + ":" + request.getMethod() + ":"
                + request.getRequestURI() + ":" + idempotencyKey;
        String resultKey = baseKey + ":result";
        String lockKey = baseKey + ":lock";

        String cached = redis.opsForValue().get(resultKey);
        if (cached != null) {
            replay(response, cached);
            return;
        }

        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            writeError(response, ErrorCode.DUPLICATE_REQUEST);
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
            byte[] body = wrapper.getContentAsByteArray();
            if (wrapper.getStatus() < 500 && body.length <= MAX_CACHE_BYTES) {
                CachedResponse result = new CachedResponse(
                        wrapper.getStatus(),
                        wrapper.getContentType(),
                        new String(body, StandardCharsets.UTF_8));
                redis.opsForValue().set(resultKey, objectMapper.writeValueAsString(result), RESULT_TTL);
            }
        } finally {
            redis.delete(lockKey);
            wrapper.copyBodyToResponse();
        }
    }

    private void replay(HttpServletResponse response, String cached) throws IOException {
        CachedResponse result = objectMapper.readValue(cached, CachedResponse.class);
        response.setStatus(result.status());
        if (result.contentType() != null) {
            response.setContentType(result.contentType());
        }
        response.setHeader("Idempotent-Replay", "true");
        response.getWriter().write(result.body());
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode));
    }

    private record CachedResponse(int status, String contentType, String body) {
    }
}
