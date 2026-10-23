package com.travelmapper.common.security;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!RequestContext.get().isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ADMIN);
        }
        return true;
    }
}
