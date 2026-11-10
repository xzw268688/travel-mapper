package com.travelmapper.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.UserStatusCache;
import com.travelmapper.controller.admin.dto.AdminQuotaRequest;
import com.travelmapper.controller.admin.dto.AdminQuotaResponse;
import com.travelmapper.controller.admin.dto.AdminStatusRequest;
import com.travelmapper.controller.admin.dto.AdminStatusResponse;
import com.travelmapper.controller.admin.dto.AdminUserDetailView;
import com.travelmapper.controller.admin.dto.AdminUserView;
import com.travelmapper.controller.admin.dto.AdminVipRequest;
import com.travelmapper.controller.admin.dto.AdminVipResponse;
import com.travelmapper.service.card.CardKeyService;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.entity.UserRecoveryCode;
import com.travelmapper.repository.enums.QuotaType;
import com.travelmapper.repository.enums.UserStatus;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.repository.mapper.UserRecoveryCodeMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.travelmapper.service.auth.SecurityNoticeService;
import com.travelmapper.service.quota.QuotaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminUserService {

    private final JdbcTemplate jdbcTemplate;
    private final UserMapper userMapper;
    private final UserStatusCache userStatusCache;
    private final QuotaService quotaService;
    private final AuditLogService auditLogService;
    private final CardKeyService cardKeyService;
    private final UserRecoveryCodeMapper recoveryCodeMapper;
    private final SecurityNoticeService securityNoticeService;

    public AdminUserService(JdbcTemplate jdbcTemplate, UserMapper userMapper,
                            UserStatusCache userStatusCache, QuotaService quotaService,
                            AuditLogService auditLogService, CardKeyService cardKeyService,
                            UserRecoveryCodeMapper recoveryCodeMapper,
                            SecurityNoticeService securityNoticeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userMapper = userMapper;
        this.userStatusCache = userStatusCache;
        this.quotaService = quotaService;
        this.auditLogService = auditLogService;
        this.cardKeyService = cardKeyService;
        this.recoveryCodeMapper = recoveryCodeMapper;
        this.securityNoticeService = securityNoticeService;
    }

    public PageResult<AdminUserView> list(String keyword, String status, PageRequest request) {
        request.validate();
        String keywordLike = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        String where = " FROM `user` WHERE (? IS NULL OR username LIKE ? OR nickname LIKE ? OR email LIKE ?) "
                + "AND (? IS NULL OR status = ?) ";

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + where, Long.class,
                keywordLike, keywordLike, keywordLike, keywordLike, statusFilter, statusFilter);
        List<AdminUserView> rows = jdbcTemplate.query(
                "SELECT id, username, nickname, email, role, status, vip_expire_at, created_at"
                        + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    long id = rs.getLong("id");
                    LocalDateTime vip = rs.getObject("vip_expire_at", LocalDateTime.class);
                    LocalDateTime created = rs.getObject("created_at", LocalDateTime.class);
                    return new AdminUserView(
                            id,
                            rs.getString("username"),
                            rs.getString("nickname"),
                            rs.getString("email"),
                            rs.getString("role"),
                            rs.getString("status"),
                            vip,
                            quotaService.freeUsedToday(id),
                            quotaService.packBalance(id),
                            created);
                },
                keywordLike, keywordLike, keywordLike, keywordLike, statusFilter, statusFilter,
                request.getSize(), request.offset());
        return PageResult.of(total == null ? 0 : total, request, rows);
    }

    @Transactional
    public AdminStatusResponse updateStatus(Long adminId, Long userId, AdminStatusRequest request) {
        User user = userMapper.selectByIdIncludingDeleted(userId);
        if (user == null || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户不存在或已注销");
        }
        UserStatus target;
        try {
            target = UserStatus.valueOf(request.getStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "status 必须为 NORMAL 或 BANNED");
        }
        if (target != UserStatus.NORMAL && target != UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "status 必须为 NORMAL 或 BANNED");
        }
        if (target == UserStatus.BANNED && user.getStatus() != UserStatus.BANNED) {
            user.setTokenVersion((user.getTokenVersion() == null ? 1 : user.getTokenVersion()) + 1);
        }
        user.setStatus(target);
        userMapper.updateById(user);
        userStatusCache.evict(userId);
        auditLogService.record(adminId, target == UserStatus.BANNED ? "USER_BAN" : "USER_UNBAN",
                "USER", userId,
                Map.of("status", target.name(), "reason", request.getReason() == null ? "" : request.getReason()),
                null);
        return new AdminStatusResponse(userId, target.name(), user.getTokenVersion());
    }

    @Transactional
    public AdminQuotaResponse adjustQuota(Long adminId, Long userId, AdminQuotaRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户不存在");
        }
        QuotaType type;
        try {
            type = QuotaType.valueOf(request.getType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "type 必须为 AI_QUOTA 或 VIP");
        }
        if (type == QuotaType.AI_QUOTA) {
            quotaService.adjustAiQuota(userId, request.getDelta(), request.getReason());
        } else {
            quotaService.adjustVip(userId, request.getDelta(), request.getReason());
        }
        User refreshed = userMapper.selectById(userId);
        auditLogService.record(adminId, "USER_QUOTA", "USER", userId,
                Map.of("type", type.name(), "delta", request.getDelta(), "reason", request.getReason()),
                null);
        return new AdminQuotaResponse(userId, type.name(),
                quotaService.packBalance(userId),
                refreshed == null ? null : refreshed.getVipExpireAt());
    }

    public AdminUserDetailView detail(Long userId) {
        User user = userMapper.selectByIdIncludingDeleted(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户不存在");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            return new AdminUserDetailView(
                    user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                    user.getRole() == null ? null : user.getRole().name(),
                    UserStatus.DELETED.name(), false, null, 0, 0, 0, 0, 0,
                    user.getCreatedAt(), List.of());
        }
        PageRequest recent = new PageRequest();
        recent.setPage(1);
        recent.setSize(10);
        return new AdminUserDetailView(
                user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getStatus().name(),
                Boolean.TRUE.equals(user.getTotpEnabled()),
                user.getVipExpireAt(),
                quotaService.freeUsedToday(userId),
                quotaService.packBalance(userId),
                countOwned("travel_note", userId),
                countOwned("itinerary", userId),
                countOwned("trip", userId),
                user.getCreatedAt(),
                cardKeyService.redemptions(userId, recent).list());
    }

    @Transactional
    public AdminVipResponse setVip(Long adminId, Long userId, AdminVipRequest request) {
        User user = userMapper.selectByIdIncludingDeleted(userId);
        if (user == null || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户不存在或已注销");
        }
        LocalDateTime expireAt = request.getExpireAt();
        user.setVipExpireAt(expireAt);
        userMapper.updateById(user);
        Map<String, Object> detail = new java.util.HashMap<>();
        detail.put("expireAt", expireAt == null ? null : expireAt.toString());
        auditLogService.record(adminId, "USER_VIP_SET", "USER", userId, detail, null);
        return new AdminVipResponse(userId, expireAt);
    }

    /**
     * 重置用户二次验证：清空密钥与恢复码，已绑定时使该用户所有会话失效。
     */
    @Transactional
    public void resetTwoFactor(Long adminId, Long userId) {
        User user = userMapper.selectByIdIncludingDeleted(userId);
        if (user == null || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户不存在或已注销");
        }
        boolean bound = Boolean.TRUE.equals(user.getTotpEnabled())
                || (user.getTotpSecret() != null && !user.getTotpSecret().isBlank());
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getTotpSecret, null)
                .set(User::getTotpEnabled, false)
                .set(User::getTotpConfirmedAt, null);
        if (bound) {
            int nextVersion = (user.getTokenVersion() == null ? 1 : user.getTokenVersion()) + 1;
            update.set(User::getTokenVersion, nextVersion);
        }
        userMapper.update(null, update);
        recoveryCodeMapper.delete(new LambdaQueryWrapper<UserRecoveryCode>()
                .eq(UserRecoveryCode::getUserId, userId));
        userStatusCache.evict(userId);
        auditLogService.record(adminId, "USER_2FA_RESET", "USER", userId,
                Map.of("bound", bound), null);
        if (bound) {
            securityNoticeService.twoFactorResetByAdmin(user.getEmail());
        }
    }

    private long countOwned(String table, Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ? AND deleted = 0",
                Long.class, userId);
        return count == null ? 0 : count;
    }
}
