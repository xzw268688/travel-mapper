package com.travelmapper.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.ratelimit.RedisRateLimiter;
import com.travelmapper.common.security.JwtService;
import com.travelmapper.common.security.UserStatusCache;
import com.travelmapper.controller.auth.dto.EmailSentResponse;
import com.travelmapper.controller.auth.dto.LoginRequest;
import com.travelmapper.controller.auth.dto.LoginResponse;
import com.travelmapper.controller.auth.dto.RegisterRequest;
import com.travelmapper.controller.auth.dto.RegisterResponse;
import com.travelmapper.controller.auth.dto.ResetPasswordRequest;
import com.travelmapper.controller.auth.dto.UserView;
import com.travelmapper.controller.user.dto.ProfileResponse;
import com.travelmapper.controller.user.dto.UpdateProfileRequest;
import com.travelmapper.repository.entity.EmailToken;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.EmailTokenType;
import com.travelmapper.repository.enums.Role;
import com.travelmapper.repository.enums.UserStatus;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.service.quota.QuotaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailTokenService emailTokenService;
    private final EmailSender emailSender;
    private final JwtService jwtService;
    private final RedisRateLimiter rateLimiter;
    private final LoginAttemptService loginAttemptService;
    private final UserStatusCache userStatusCache;
    private final QuotaService quotaService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       EmailTokenService emailTokenService, EmailSender emailSender,
                       JwtService jwtService, RedisRateLimiter rateLimiter,
                       LoginAttemptService loginAttemptService, UserStatusCache userStatusCache,
                       QuotaService quotaService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailTokenService = emailTokenService;
        this.emailSender = emailSender;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.loginAttemptService = loginAttemptService;
        this.userStatusCache = userStatusCache;
        this.quotaService = quotaService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!Boolean.TRUE.equals(request.getAgreeTerms())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请先同意用户协议与隐私政策");
        }
        String username = request.getUsername().trim();
        String email = normalizeEmail(request.getEmail());
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (usernameCount > 0 || emailCount > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        if (!rateLimiter.tryAcquire("rate:email:" + email, 1, 60)) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_TOO_FREQUENT);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank()
                ? username : request.getNickname().trim());
        user.setAvatarPreset("preset_01");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.NORMAL);
        user.setEmailVerified(false);
        user.setTokenVersion(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }

        EmailToken token = emailTokenService.create(
                user.getId(), email, EmailTokenType.VERIFY, Duration.ofMinutes(30));
        boolean sent = send(email, "验证你的 TravelMapper 邮箱",
                "你的验证令牌：" + token.getToken() + "，30 分钟内有效。");
        return new RegisterResponse(user.getId(), sent, token.getExpireAt());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailToken token = emailTokenService.consume(rawToken, EmailTokenType.VERIFY);
        User user = userMapper.selectById(token.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        user.setEmailVerified(true);
        userMapper.updateById(user);
    }

    @Transactional
    public EmailSentResponse resendVerify(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (!rateLimiter.tryAcquire("rate:email:" + email, 1, 60)) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_TOO_FREQUENT);
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user != null
                && !Boolean.TRUE.equals(user.getEmailVerified())
                && user.getStatus() == UserStatus.NORMAL) {
            EmailToken token = emailTokenService.create(
                    user.getId(), email, EmailTokenType.VERIFY, Duration.ofMinutes(30));
            send(email, "验证你的 TravelMapper 邮箱",
                    "你的验证令牌：" + token.getToken() + "，30 分钟内有效。");
        }
        // Never disclose whether the email is registered.
        return new EmailSentResponse(true);
    }

    public LoginResponse login(LoginRequest request) {
        String account = request.getAccount().trim();
        if (account.contains("@")) {
            account = account.toLowerCase(Locale.ROOT);
        }
        loginAttemptService.checkNotLocked(account);

        User user = userMapper.selectByAccountIncludingDeleted(account);
        if (user == null) {
            loginAttemptService.recordFailure(account);
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(account);
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD);
        }

        loginAttemptService.clear(account);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        String jwt = jwtService.issue(user.getId(), user.getRole().name(), user.getTokenVersion());
        return new LoginResponse(jwt, jwtService.ttlSeconds(), toUserView(user));
    }

    @Transactional
    public EmailSentResponse forgotPassword(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (!rateLimiter.tryAcquire("rate:email:" + email, 1, 60)) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_TOO_FREQUENT);
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user != null && user.getStatus() == UserStatus.NORMAL) {
            EmailToken token = emailTokenService.create(
                    user.getId(), email, EmailTokenType.RESET, Duration.ofMinutes(30));
            send(email, "重置你的 TravelMapper 密码",
                    "你的重置令牌：" + token.getToken() + "，30 分钟内有效。");
        }
        return new EmailSentResponse(true);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        EmailToken token = emailTokenService.consume(request.getToken(), EmailTokenType.RESET);
        User user = userMapper.selectById(token.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setTokenVersion((user.getTokenVersion() == null ? 1 : user.getTokenVersion()) + 1);
        userMapper.updateById(user);
        userStatusCache.evict(user.getId());
    }

    public void logout() {
        // Stateless JWT: the client discards the token. Blacklisting is optional in V1.
    }

    public ProfileResponse profile(Long userId) {
        User user = requireActive(userId);
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarPreset(),
                user.getRole().name(),
                user.getVipExpireAt(),
                user.getCreatedAt(),
                quotaService.getSummary(userId));
    }

    public UserView updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireActive(userId);
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getAvatarPreset() != null) {
            user.setAvatarPreset(request.getAvatarPreset());
        }
        userMapper.updateById(user);
        return toUserView(user);
    }

    public UserView toUserView(User user) {
        String plan = user.getVipExpireAt() != null
                && user.getVipExpireAt().isAfter(LocalDateTime.now()) ? "VIP" : "FREE";
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarPreset(),
                user.getRole().name(),
                plan,
                user.getVipExpireAt());
    }

    public User requireActive(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.JWT_INVALID);
        }
        return user;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean send(String to, String subject, String body) {
        try {
            emailSender.send(to, subject, body);
            return true;
        } catch (Exception ex) {
            log.error("email send failed subject={}", subject, ex);
            return false;
        }
    }
}
