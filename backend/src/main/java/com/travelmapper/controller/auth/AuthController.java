package com.travelmapper.controller.auth;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.auth.dto.EmailSentResponse;
import com.travelmapper.controller.auth.dto.ForgotPasswordRequest;
import com.travelmapper.controller.auth.dto.LoginRequest;
import com.travelmapper.controller.auth.dto.LoginResponse;
import com.travelmapper.controller.auth.dto.RegisterRequest;
import com.travelmapper.controller.auth.dto.RegisterResponse;
import com.travelmapper.controller.auth.dto.ResendVerifyRequest;
import com.travelmapper.controller.auth.dto.ResetPasswordRequest;
import com.travelmapper.controller.auth.dto.VerifyEmailRequest;
import com.travelmapper.service.auth.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getToken());
        return ApiResponse.ok();
    }

    @PostMapping("/resend-verify")
    public ApiResponse<EmailSentResponse> resendVerify(@Valid @RequestBody ResendVerifyRequest request) {
        return ApiResponse.ok(authService.resendVerify(request.getEmail()));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<EmailSentResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ApiResponse.ok(authService.forgotPassword(request.getEmail()));
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
}
