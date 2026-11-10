package com.travelmapper.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.crypto.AesCryptoService;
import com.travelmapper.common.security.JwtService;
import com.travelmapper.common.security.TotpService;
import com.travelmapper.common.security.UserStatusCache;
import com.travelmapper.controller.auth.dto.EmailSentResponse;
import com.travelmapper.controller.user.dto.ChangeEmailRequest;
import com.travelmapper.controller.user.dto.ChangePasswordRequest;
import com.travelmapper.controller.user.dto.TokenResponse;
import com.travelmapper.controller.user.dto.TwoFactorCodesResponse;
import com.travelmapper.controller.user.dto.TwoFactorSetupResponse;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.entity.UserRecoveryCode;
import com.travelmapper.repository.enums.UserStatus;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.repository.mapper.UserRecoveryCodeMapper;
import com.travelmapper.service.audit.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 登录态下的账号安全自助：改密、改邮箱、TOTP 二次验证与恢复码。
 */
@Slf4j
@Service
public class UserAccountSecurityService {

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_RAW_LENGTH = 10;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String ISSUER = "TravelMapper";

    private final UserMapper userMapper;
    private final UserRecoveryCodeMapper recoveryCodeMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailCodeService emailCodeService;
    private final JwtService jwtService;
    private final UserStatusCache userStatusCache;
    private final SecurityNoticeService securityNoticeService;
    private final AuditLogService auditLogService;
    private final AesCryptoService aesCryptoService;
    private final TotpService totpService;
    private final SecureRandom random = new SecureRandom();

    public UserAccountSecurityService(UserMapper userMapper,
                                      UserRecoveryCodeMapper recoveryCodeMapper,
                                      PasswordEncoder passwordEncoder,
                                      EmailCodeService emailCodeService,
                                      JwtService jwtService,
                                      UserStatusCache userStatusCache,
                                      SecurityNoticeService securityNoticeService,
                                      AuditLogService auditLogService,
                                      AesCryptoService aesCryptoService,
                                      TotpService totpService) {
        this.userMapper = userMapper;
        this.recoveryCodeMapper = recoveryCodeMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailCodeService = emailCodeService;
        this.jwtService = jwtService;
        this.userStatusCache = userStatusCache;
        this.securityNoticeService = securityNoticeService;
        this.auditLogService = auditLogService;
        this.aesCryptoService = aesCryptoService;
        this.totpService = totpService;
    }

    @Transactional
    public TokenResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = requireActive(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD);
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        rotateSessions(user);
        userMapper.updateById(user);
        auditLogService.record(userId, "USER_PASSWORD_CHANGE", "USER", userId,
                Map.of("changed", "password"), null);
        securityNoticeService.passwordChanged(user.getEmail());
        return issueToken(user);
    }

    public EmailSentResponse sendEmailChangeCode(Long userId, String rawEmail, String clientIp) {
        User user = requireActive(userId);
        String email = normalizeEmail(rawEmail);
        if (email.equals(user.getEmail())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "新邮箱不能与当前邮箱相同");
        }
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        emailCodeService.checkSendLimits(email, clientIp);
        boolean sent = emailCodeService.issue(email, EmailCodeService.Scene.CHANGE_EMAIL);
        return new EmailSentResponse(sent);
    }

    @Transactional
    public TokenResponse changeEmail(Long userId, ChangeEmailRequest request) {
        User user = requireActive(userId);
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD);
        }
        String email = normalizeEmail(request.getNewEmail());
        if (email.equals(user.getEmail())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "新邮箱不能与当前邮箱相同");
        }
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        emailCodeService.verify(email, EmailCodeService.Scene.CHANGE_EMAIL, request.getCode());

        String oldEmail = user.getEmail();
        user.setEmail(email);
        rotateSessions(user);
        userMapper.updateById(user);
        auditLogService.record(userId, "USER_EMAIL_CHANGE", "USER", userId,
                Map.of("oldEmail", maskEmail(oldEmail), "newEmail", maskEmail(email)), null);
        securityNoticeService.emailChanged(oldEmail, email);
        return issueToken(user);
    }

    @Transactional
    public TwoFactorSetupResponse setupTwoFactor(Long userId) {
        User user = requireActive(userId);
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_ALREADY_ENABLED);
        }
        String secret = totpService.generateSecret();
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getTotpSecret, aesCryptoService.encrypt(secret))
                .set(User::getTotpEnabled, false)
                .set(User::getTotpConfirmedAt, null));
        return new TwoFactorSetupResponse(secret,
                totpService.otpauthUri(ISSUER, user.getUsername(), secret));
    }

    @Transactional
    public TwoFactorCodesResponse enableTwoFactor(Long userId, String code) {
        User user = requireActive(userId);
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_ALREADY_ENABLED);
        }
        if (!verifyTotp(user, code)) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_INVALID);
        }
        List<String> codes = replaceRecoveryCodes(userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getTotpEnabled, true)
                .set(User::getTotpConfirmedAt, LocalDateTime.now()));
        userStatusCache.evict(userId);
        auditLogService.record(userId, "USER_2FA_ENABLE", "USER", userId, Map.of(), null);
        securityNoticeService.twoFactorEnabled(user.getEmail());
        return new TwoFactorCodesResponse(codes);
    }

    @Transactional
    public void disableTwoFactor(Long userId, String password, String code) {
        User user = requireActive(userId);
        requireEnabled(user);
        requirePasswordAndTotp(user, password, code);
        deleteRecoveryCodes(userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getTotpSecret, null)
                .set(User::getTotpEnabled, false)
                .set(User::getTotpConfirmedAt, null));
        userStatusCache.evict(userId);
        auditLogService.record(userId, "USER_2FA_DISABLE", "USER", userId, Map.of(), null);
        securityNoticeService.twoFactorDisabled(user.getEmail());
    }

    @Transactional
    public TwoFactorCodesResponse regenerateRecoveryCodes(Long userId, String password, String code) {
        User user = requireActive(userId);
        requireEnabled(user);
        requirePasswordAndTotp(user, password, code);
        List<String> codes = replaceRecoveryCodes(userId);
        auditLogService.record(userId, "USER_2FA_RECOVERY_REGENERATE", "USER", userId,
                Map.of(), null);
        securityNoticeService.recoveryCodesRegenerated(user.getEmail());
        return new TwoFactorCodesResponse(codes);
    }

    /** 登录第二步：校验 6 位 TOTP 或一次性恢复码。 */
    @Transactional
    public boolean verifySecondFactor(User user, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        if (verifyTotp(user, code.trim())) {
            return true;
        }
        String normalized = normalizeRecoveryCode(code);
        if (normalized.isEmpty()) {
            return false;
        }
        List<UserRecoveryCode> codes = recoveryCodeMapper.selectList(
                new LambdaQueryWrapper<UserRecoveryCode>()
                        .eq(UserRecoveryCode::getUserId, user.getId()));
        for (UserRecoveryCode candidate : codes) {
            if (passwordEncoder.matches(normalized, candidate.getCodeHash())) {
                recoveryCodeMapper.deleteById(candidate.getId());
                return true;
            }
        }
        return false;
    }

    private boolean verifyTotp(User user, String code) {
        if (user.getTotpSecret() == null || user.getTotpSecret().isBlank()) {
            return false;
        }
        try {
            return totpService.verify(aesCryptoService.decrypt(user.getTotpSecret()), code);
        } catch (RuntimeException ex) {
            log.warn("totp verify failed due to secret decrypt error userId={}: {}",
                    user.getId(), ex.toString());
            return false;
        }
    }

    private void requireEnabled(User user) {
        if (!Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_NOT_ENABLED);
        }
    }

    private void requirePasswordAndTotp(User user, String password, String code) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD);
        }
        if (!verifyTotp(user, code)) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_INVALID);
        }
    }

    private List<String> replaceRecoveryCodes(Long userId) {
        deleteRecoveryCodes(userId);
        Set<String> unique = new LinkedHashSet<>();
        while (unique.size() < RECOVERY_CODE_COUNT) {
            unique.add(randomRecoveryCode());
        }
        List<String> plain = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (String raw : unique) {
            plain.add(raw.substring(0, 5) + "-" + raw.substring(5));
            UserRecoveryCode entity = new UserRecoveryCode();
            entity.setUserId(userId);
            entity.setCodeHash(passwordEncoder.encode(raw));
            recoveryCodeMapper.insert(entity);
        }
        return plain;
    }

    private void deleteRecoveryCodes(Long userId) {
        recoveryCodeMapper.delete(new LambdaQueryWrapper<UserRecoveryCode>()
                .eq(UserRecoveryCode::getUserId, userId));
    }

    private String randomRecoveryCode() {
        StringBuilder out = new StringBuilder(RECOVERY_CODE_RAW_LENGTH);
        for (int i = 0; i < RECOVERY_CODE_RAW_LENGTH; i++) {
            out.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
        }
        return out.toString();
    }

    private void rotateSessions(User user) {
        user.setTokenVersion((user.getTokenVersion() == null ? 1 : user.getTokenVersion()) + 1);
        userStatusCache.evict(user.getId());
    }

    private TokenResponse issueToken(User user) {
        String token = jwtService.issue(user.getId(), user.getRole().name(), user.getTokenVersion());
        return new TokenResponse(token, jwtService.ttlSeconds());
    }

    private User requireActive(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.JWT_INVALID);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        return user;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRecoveryCode(String code) {
        return code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String maskEmail(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}