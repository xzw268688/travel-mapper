package com.travelmapper.controller.user;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.auth.dto.EmailSentResponse;
import com.travelmapper.controller.auth.dto.UserView;
import com.travelmapper.controller.user.dto.ChangeEmailRequest;
import com.travelmapper.controller.user.dto.ChangePasswordRequest;
import com.travelmapper.controller.user.dto.DeleteAccountRequest;
import com.travelmapper.controller.user.dto.DeleteAccountResponse;
import com.travelmapper.controller.user.dto.ProfileResponse;
import com.travelmapper.controller.user.dto.SendEmailCodeRequest;
import com.travelmapper.controller.user.dto.TokenResponse;
import com.travelmapper.controller.user.dto.TwoFactorCodeRequest;
import com.travelmapper.controller.user.dto.TwoFactorCodesResponse;
import com.travelmapper.controller.user.dto.TwoFactorCredentialRequest;
import com.travelmapper.controller.user.dto.TwoFactorSetupResponse;
import com.travelmapper.controller.user.dto.UpdateProfileRequest;
import com.travelmapper.service.auth.AccountDeletionService;
import com.travelmapper.service.auth.AuthService;
import com.travelmapper.service.auth.UserAccountSecurityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User")
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthService authService;
    private final AccountDeletionService accountDeletionService;
    private final UserAccountSecurityService accountSecurityService;

    public UserController(AuthService authService, AccountDeletionService accountDeletionService,
                          UserAccountSecurityService accountSecurityService) {
        this.authService = authService;
        this.accountDeletionService = accountDeletionService;
        this.accountSecurityService = accountSecurityService;
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> profile() {
        return ApiResponse.ok(authService.profile(CurrentUser.id()));
    }

    @PutMapping("/profile")
    public ApiResponse<UserView> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(authService.updateProfile(CurrentUser.id(), request));
    }

    @PostMapping("/logout-account")
    public ApiResponse<DeleteAccountResponse> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request) {
        return ApiResponse.ok(accountDeletionService.delete(CurrentUser.id(), request));
    }

    @PutMapping("/password")
    public ApiResponse<TokenResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok(accountSecurityService.changePassword(CurrentUser.id(), request));
    }

    @PostMapping("/email/send-code")
    public ApiResponse<EmailSentResponse> sendEmailChangeCode(
            @Valid @RequestBody SendEmailCodeRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(accountSecurityService.sendEmailChangeCode(
                CurrentUser.id(), request.getEmail(), clientIp(servletRequest)));
    }

    @PutMapping("/email")
    public ApiResponse<TokenResponse> changeEmail(@Valid @RequestBody ChangeEmailRequest request) {
        return ApiResponse.ok(accountSecurityService.changeEmail(CurrentUser.id(), request));
    }

    @PostMapping("/2fa/setup")
    public ApiResponse<TwoFactorSetupResponse> setupTwoFactor() {
        return ApiResponse.ok(accountSecurityService.setupTwoFactor(CurrentUser.id()));
    }

    @PostMapping("/2fa/enable")
    public ApiResponse<TwoFactorCodesResponse> enableTwoFactor(
            @Valid @RequestBody TwoFactorCodeRequest request) {
        return ApiResponse.ok(accountSecurityService.enableTwoFactor(
                CurrentUser.id(), request.getCode()));
    }

    @PostMapping("/2fa/disable")
    public ApiResponse<Void> disableTwoFactor(
            @Valid @RequestBody TwoFactorCredentialRequest request) {
        accountSecurityService.disableTwoFactor(
                CurrentUser.id(), request.getPassword(), request.getCode());
        return ApiResponse.ok();
    }

    @PostMapping("/2fa/recovery-codes")
    public ApiResponse<TwoFactorCodesResponse> regenerateRecoveryCodes(
            @Valid @RequestBody TwoFactorCredentialRequest request) {
        return ApiResponse.ok(accountSecurityService.regenerateRecoveryCodes(
                CurrentUser.id(), request.getPassword(), request.getCode()));
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
