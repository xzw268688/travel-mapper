package com.travelmapper.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdentityFilter extends OncePerRequestFilter {

    private final AppProperties properties;
    private final JwtService jwtService;
    private final JwtDenylistService jwtDenylistService;
    private final UserStatusCache userStatusCache;
    private final ObjectMapper objectMapper;
    private final OnlineUserRegistry onlineUserRegistry;

    public RequestIdentityFilter(AppProperties properties, JwtService jwtService,
                                 JwtDenylistService jwtDenylistService,
                                 UserStatusCache userStatusCache, ObjectMapper objectMapper,
                                 OnlineUserRegistry onlineUserRegistry) {
        this.properties = properties;
        this.jwtService = jwtService;
        this.jwtDenylistService = jwtDenylistService;
        this.userStatusCache = userStatusCache;
        this.objectMapper = objectMapper;
        this.onlineUserRegistry = onlineUserRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean devStubEnabled = properties.getSecurity().isAdminStubEnabled();
        boolean adminPath = path.startsWith("/api/admin/");
        boolean requiresAuth = SecurityPaths.requiresAuth(path, devStubEnabled);
        RequestIdentity identity = RequestIdentity.ANONYMOUS;

        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                identity = resolveJwt(authorization.substring("Bearer ".length()).trim(), adminPath);
            } catch (BusinessException ex) {
                if (requiresAuth || adminPath) {
                    writeError(response, ex.getErrorCode());
                    return;
                }
                // Public endpoints tolerate stale credentials (e.g. login after expiry).
            }
        } else if (devStubEnabled) {
            identity = resolveDevHeaders(request);
        }

        if (!identity.isAuthenticated() && requiresAuth) {
            writeError(response, ErrorCode.JWT_INVALID);
            return;
        }

        request.setAttribute(RequestIdentity.ATTRIBUTE, identity);
        RequestContext.set(identity);
        onlineUserRegistry.touch(identity, clientIp(request), request.getHeader("User-Agent"));
        try {
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }

    private RequestIdentity resolveJwt(String token, boolean adminPath) {
        JwtPayload payload = jwtService.parse(token);
        if (payload.purpose() != null) {
            // 二次验证挑战凭证只能用于 /api/auth/login/2fa，不能访问业务接口。
            throw new BusinessException(ErrorCode.JWT_INVALID);
        }
        UserAuthSnapshot snapshot = userStatusCache.get(payload.userId());
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.JWT_INVALID);
        }
        if ("BANNED".equals(snapshot.status())) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        if ("DELETED".equals(snapshot.status())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
        }
        if (payload.tokenVersion() != snapshot.tokenVersion()) {
            throw new BusinessException(ErrorCode.JWT_INVALID);
        }
        if (jwtDenylistService.isRevoked(payload.jti())) {
            throw new BusinessException(ErrorCode.JWT_INVALID);
        }
        if (adminPath && properties.getSecurity().isRequireAdmin2fa()
                && "ADMIN".equals(snapshot.role()) && !snapshot.totpEnabled()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_REQUIRED);
        }
        return new RequestIdentity(snapshot.userId(), snapshot.role());
    }

    private RequestIdentity resolveDevHeaders(HttpServletRequest request) {
        String rawUserId = request.getHeader("X-User-Id");
        String rawRole = request.getHeader("X-User-Role");
        if (rawUserId != null && rawUserId.matches("\\d{1,19}")) {
            String role = "ADMIN".equalsIgnoreCase(rawRole) ? "ADMIN" : "USER";
            return new RequestIdentity(Long.valueOf(rawUserId), role);
        }
        return RequestIdentity.ANONYMOUS;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode));
    }
}
