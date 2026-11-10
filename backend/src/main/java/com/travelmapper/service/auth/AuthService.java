package com.travelmapper.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.ratelimit.RedisRateLimiter;
import com.travelmapper.common.security.JwtDenylistService;
import com.travelmapper.common.security.JwtPayload;
import com.travelmapper.common.security.JwtService;
import com.travelmapper.common.security.OnlineUserRegistry;
import com.travelmapper.common.security.UserStatusCache;
import com.travelmapper.controller.auth.dto.EmailSentResponse;
import com.travelmapper.controller.auth.dto.LoginRequest;
import com.travelmapper.controller.auth.dto.LoginResponse;
import com.travelmapper.controller.auth.dto.RegisterRequest;
import com.travelmapper.controller.auth.dto.RegisterResponse;
import com.travelmapper.controller.auth.dto.ResetPasswordRequest;
import com.travelmapper.controller.auth.dto.TwoFactorLoginRequest;
import com.travelmapper.controller.auth.dto.UserView;
import com.travelmapper.controller.user.dto.ProfileResponse;
import com.travelmapper.controller.user.dto.UpdateProfileRequest;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.Role;
import com.travelmapper.repository.enums.UserStatus;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.travelmapper.service.audit.LoginLogService;
import com.travelmapper.service.quota.QuotaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailCodeService emailCodeService;
    private final JwtService jwtService;
    private final JwtDenylistService jwtDenylistService;
    private final LoginAttemptService loginAttemptService;
    private final UserStatusCache userStatusCache;
    private final OnlineUserRegistry onlineUserRegistry;
    private final QuotaService quotaService;
    private final LoginLogService loginLogService;
    private final AuditLogService auditLogService;
    private final UserAccountSecurityService accountSecurityService;
    private final StringRedisTemplate redis;
    private final RedisRateLimiter rateLimiter;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       EmailCodeService emailCodeService,
                       JwtService jwtService,
                       JwtDenylistService jwtDenylistService,
                       LoginAttemptService loginAttemptService, UserStatusCache userStatusCache,
                       OnlineUserRegistry onlineUserRegistry,
                       QuotaService quotaService, LoginLogService loginLogService,
                       AuditLogService auditLogService,
                       UserAccountSecurityService accountSecurityService,
                       StringRedisTemplate redis, RedisRateLimiter rateLimiter) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailCodeService = emailCodeService;
        this.jwtService = jwtService;
        this.jwtDenylistService = jwtDenylistService;
        this.loginAttemptService = loginAttemptService;
        this.userStatusCache = userStatusCache;
        this.onlineUserRegistry = onlineUserRegistry;
        this.quotaService = quotaService;
        this.loginLogService = loginLogService;
        this.auditLogService = auditLogService;
        this.accountSecurityService = accountSecurityService;
        this.redis = redis;
        this.rateLimiter = rateLimiter;
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
        // 先查重、再校验验证码：用户名/邮箱冲突时不消耗验证码。
        emailCodeService.verify(email, EmailCodeService.Scene.VERIFY, request.getCode());

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank()
                ? username : request.getNickname().trim());
        user.setAvatarPreset("preset_01");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.NORMAL);
        user.setEmailVerified(true);
        user.setTokenVersion(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }

        return new RegisterResponse(user.getId());
    }

    /**
     * 发送验证码：无论邮箱是否存在都返回成功，避免账号枚举。
     */
    public EmailSentResponse sendCode(String rawEmail, String rawScene, String clientIp) {
        String email = normalizeEmail(rawEmail);
        EmailCodeService.Scene scene;
        try {
            scene = EmailCodeService.Scene.valueOf(rawScene.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "scene 必须为 VERIFY 或 RESET");
        }
        if (scene == EmailCodeService.Scene.CHANGE_EMAIL) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "修改邮箱验证码需登录后操作");
        }
        emailCodeService.checkSendLimits(email, clientIp);
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        boolean eligible;
        if (scene == EmailCodeService.Scene.VERIFY) {
            // 注册前校验：未注册邮箱需要收码；已注册且已验证的邮箱不再发码。
            eligible = user == null
                    || (user.getStatus() == UserStatus.NORMAL
                    && !Boolean.TRUE.equals(user.getEmailVerified()));
        } else {
            eligible = user != null && user.getStatus() == UserStatus.NORMAL;
        }
        boolean sent = true;
        if (eligible) {
            sent = emailCodeService.issue(email, scene);
        }
        return new EmailSentResponse(sent);
    }

    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        String account = request.getAccount().trim();
        if (account.contains("@")) {
            account = account.toLowerCase(Locale.ROOT);
        }
        try {
            loginAttemptService.checkNotLocked(account);
        } catch (BusinessException ex) {
            String reason = ex.getErrorCode() == ErrorCode.RATE_LIMITED
                    ? LoginLogService.RATE_LIMITED : ex.getErrorCode().name();
            recordLogin(null, account, false, reason, clientIp, userAgent);
            throw ex;
        }

        User user = userMapper.selectByAccountIncludingDeleted(account);
        if (user == null) {
            recordLogin(null, account, false, LoginLogService.USER_NOT_FOUND, clientIp, userAgent);
            loginAttemptService.recordFailure(account);
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            recordLogin(user, account, false, LoginLogService.ACCOUNT_DELETED, clientIp, userAgent);
            throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            recordLogin(user, account, false, LoginLogService.ACCOUNT_BANNED, clientIp, userAgent);
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            recordLogin(user, account, false, LoginLogService.EMAIL_NOT_VERIFIED, clientIp, userAgent);
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordLogin(user, account, false, LoginLogService.BAD_CREDENTIALS, clientIp, userAgent);
            loginAttemptService.recordFailure(account);
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD);
        }

        loginAttemptService.clear(account);
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            String challenge = jwtService.issueTwoFactorChallenge(
                    user.getId(), user.getTokenVersion());
            return LoginResponse.twoFactorChallenge(challenge);
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        recordLogin(user, account, true, null, clientIp, userAgent);
        String jwt = jwtService.issue(user.getId(), user.getRole().name(), user.getTokenVersion());
        return LoginResponse.authenticated(jwt, jwtService.ttlSeconds(), toUserView(user));
    }

    /** 登录第二步：用 5 分钟挑战凭证 + TOTP/恢复码换取正式 JWT。 */
    public LoginResponse loginTwoFactor(TwoFactorLoginRequest request, String clientIp, String userAgent) {
        JwtPayload payload;
        try {
            payload = jwtService.parse(request.getChallengeToken());
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_INVALID);
        }
        if (!JwtService.PURPOSE_TWO_FACTOR.equals(payload.purpose())
                || payload.jti() == null || payload.jti().isBlank()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_INVALID);
        }
        String jti = payload.jti();
        String usedKey = "auth:2fa:used:" + jti;
        if (Boolean.TRUE.equals(redis.hasKey(usedKey))) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_INVALID);
        }
        if (!rateLimiter.tryAcquire("auth:2fa:attempt:" + jti, 5,
                JwtService.TWO_FACTOR_CHALLENGE_TTL_SECONDS)) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_INVALID);
        }

        User user = userMapper.selectByIdIncludingDeleted(payload.userId());
        if (user == null || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (!Boolean.TRUE.equals(user.getTotpEnabled())
                || payload.tokenVersion() != user.getTokenVersion()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_INVALID);
        }
        if (!accountSecurityService.verifySecondFactor(user, request.getCode())) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_INVALID);
        }

        redis.opsForValue().set(usedKey, "1",
                Duration.ofSeconds(JwtService.TWO_FACTOR_CHALLENGE_TTL_SECONDS));
        loginAttemptService.clear(user.getUsername());
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        recordLogin(user, user.getUsername(), true, null, clientIp, userAgent);
        String jwt = jwtService.issue(user.getId(), user.getRole().name(), user.getTokenVersion());
        return LoginResponse.authenticated(jwt, jwtService.ttlSeconds(), toUserView(user));
    }

    private void recordLogin(User user, String account, boolean success, String reason,
                             String clientIp, String userAgent) {
        loginLogService.record(
                user == null ? null : user.getId(),
                account,
                user == null || user.getRole() == null ? null : user.getRole().name(),
                success,
                reason,
                clientIp,
                userAgent);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        emailCodeService.verify(email, EmailCodeService.Scene.RESET, request.getCode());
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setTokenVersion((user.getTokenVersion() == null ? 1 : user.getTokenVersion()) + 1);
        userMapper.updateById(user);
        userStatusCache.evict(user.getId());
    }

    /**
     * 只吊销当前 Token：按 jti 写入黑名单，TTL 取该 Token 的剩余有效期。
     * 不含 jti 的旧 Token 无法单独吊销，保留至自然过期。
     */
    public void logout(Long userId, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            JwtPayload payload = jwtService.parse(token);
            if (payload.jti() == null || payload.jti().isBlank()) {
                log.debug("legacy token without jti cannot be revoked individually userId={}", userId);
                return;
            }
            long ttlSeconds = payload.expiresAtEpochSecond() - Instant.now().getEpochSecond();
            jwtDenylistService.revoke(payload.jti(), ttlSeconds);
        } catch (BusinessException ex) {
            log.debug("logout token parse failed userId={}", userId, ex);
        }
    }

    /** 退出所有设备：token_version 自增，使该用户全部已签发 JWT 失效。 */
    @Transactional
    public void logoutAll(Long userId) {
        User user = requireActive(userId);
        user.setTokenVersion((user.getTokenVersion() == null ? 1 : user.getTokenVersion()) + 1);
        userMapper.updateById(user);
        userStatusCache.evict(userId);
        onlineUserRegistry.remove(userId);
        auditLogService.record(userId, "AUTH_LOGOUT_ALL", "USER", userId, Map.of(), null);
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
                Boolean.TRUE.equals(user.getTotpEnabled()),
                user.getVipExpireAt(),
                user.getCreatedAt(),
                quotaService.getSummary(userId));
    }

    public UserView updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireActive(userId);
        List<String> changed = new ArrayList<>();
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname().trim());
            changed.add("nickname");
        }
        if (request.getAvatarPreset() != null) {
            user.setAvatarPreset(request.getAvatarPreset());
            changed.add("avatarPreset");
        }
        userMapper.updateById(user);
        auditLogService.record(userId, "USER_PROFILE_UPDATE", "USER", userId,
                Map.of("changed", changed), null);
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
}
