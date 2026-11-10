package com.travelmapper.controller.auth;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.auth.dto.EmailSentResponse;
import com.travelmapper.controller.auth.dto.LoginRequest;
import com.travelmapper.controller.auth.dto.LoginResponse;
import com.travelmapper.controller.auth.dto.RegisterRequest;
import com.travelmapper.controller.auth.dto.RegisterResponse;
import com.travelmapper.controller.auth.dto.ResetPasswordRequest;
import com.travelmapper.controller.auth.dto.SendCodeRequest;
import com.travelmapper.controller.auth.dto.TwoFactorLoginRequest;
import com.travelmapper.service.auth.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    /** 发送验证码：scene 为 VERIFY（注册验证）或 RESET（找回密码）。 */
    @PostMapping("/send-code")
    public ApiResponse<EmailSentResponse> sendCode(@Valid @RequestBody SendCodeRequest request,
                                                   HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.sendCode(
                request.getEmail(), request.getScene(), clientIp(servletRequest)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.login(
                request, clientIp(servletRequest), clientUserAgent(servletRequest)));
    }

    @PostMapping("/login/2fa")
    public ApiResponse<LoginResponse> loginTwoFactor(@Valid @RequestBody TwoFactorLoginRequest request,
                                                     HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.loginTwoFactor(
                request, clientIp(servletRequest), clientUserAgent(servletRequest)));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(CurrentUser.id(), bearerToken(authorization));
        return ApiResponse.ok();
    }

    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll() {
        authService.logoutAll(CurrentUser.id());
        return ApiResponse.ok();
    }

    private String bearerToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return null;
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

    private String clientUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}