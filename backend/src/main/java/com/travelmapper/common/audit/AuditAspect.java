package com.travelmapper.common.audit;

import com.travelmapper.common.security.RequestContext;
import com.travelmapper.service.audit.AuditLogService;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AuditAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("method", joinPoint.getSignature().toShortString());
            detail.put("args", sanitizeArgs(joinPoint.getArgs()));
            auditLogService.record(
                    RequestContext.userIdOrNull(),
                    auditable.action(),
                    auditable.targetType(),
                    resolveTargetId(joinPoint, auditable.targetIdSpel()),
                    detail,
                    currentIp());
        } catch (Exception ex) {
            // Audit must never break the business operation.
            log.warn("audit write failed", ex);
        }
        return result;
    }

    private List<Object> sanitizeArgs(Object[] args) {
        List<Object> safe = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof ServletRequest || arg instanceof ServletResponse || arg instanceof MultipartFile) {
                continue;
            }
            safe.add(arg);
        }
        return safe;
    }

    private Long resolveTargetId(ProceedingJoinPoint joinPoint, String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    null, ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod(),
                    joinPoint.getArgs(), parameterNameDiscoverer);
            Expression parsed = parser.parseExpression(expression);
            Object value = parsed.getValue(context);
            return value instanceof Number number ? number.longValue() : null;
        } catch (Exception ex) {
            log.debug("failed to resolve audit target id from {}", expression, ex);
            return null;
        }
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
