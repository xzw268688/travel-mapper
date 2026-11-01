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
import com.travelmapper.service.auth.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        CurrentUser.id();
        authService.logout();
        return ApiResponse.ok();
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
}