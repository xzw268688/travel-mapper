package com.travelmapper.controller.user;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.auth.dto.UserView;
import com.travelmapper.controller.user.dto.DeleteAccountRequest;
import com.travelmapper.controller.user.dto.DeleteAccountResponse;
import com.travelmapper.controller.user.dto.ProfileResponse;
import com.travelmapper.controller.user.dto.UpdateProfileRequest;
import com.travelmapper.service.auth.AccountDeletionService;
import com.travelmapper.service.auth.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public UserController(AuthService authService, AccountDeletionService accountDeletionService) {
        this.authService = authService;
        this.accountDeletionService = accountDeletionService;
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
}
