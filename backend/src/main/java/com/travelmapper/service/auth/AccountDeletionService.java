package com.travelmapper.service.auth;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.security.UserStatusCache;
import com.travelmapper.controller.user.dto.DeleteAccountRequest;
import com.travelmapper.controller.user.dto.DeleteAccountResponse;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.Role;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.travelmapper.service.quota.QuotaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AccountDeletionService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final UserStatusCache userStatusCache;
    private final QuotaService quotaService;
    private final AuditLogService auditLogService;

    public AccountDeletionService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                                  JdbcTemplate jdbcTemplate, UserStatusCache userStatusCache,
                                  QuotaService quotaService, AuditLogService auditLogService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.userStatusCache = userStatusCache;
        this.quotaService = quotaService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public DeleteAccountResponse delete(Long userId, DeleteAccountRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.JWT_INVALID);
        }
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ADMIN);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD);
        }
        if (!"注销账号".equals(request.getConfirmText())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "确认文案必须为“注销账号”");
        }

        String anonymizedUsername = "deleted_" + userId;
        String anonymizedEmail = "deleted_" + userId + "@deleted.local";

        jdbcTemplate.update("UPDATE `user` SET status = 'DELETED', deleted = 1, username = ?, email = ?, "
                        + "nickname = '已注销用户', password_hash = '', vip_expire_at = NULL, "
                        + "token_version = token_version + 1 WHERE id = ?",
                anonymizedUsername, anonymizedEmail, userId);
        jdbcTemplate.update("UPDATE email_token SET used = 1 WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE trip SET deleted = 1, name = '已注销旅行' WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE itinerary SET deleted = 1, title = '已注销行程', "
                + "guides_json = NULL, preparations_json = NULL WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE itinerary_spot s "
                + "JOIN itinerary_day d ON s.day_id = d.id "
                + "JOIN itinerary i ON d.itinerary_id = i.id "
                + "SET s.name = '', s.description = '' WHERE i.user_id = ?", userId);
        jdbcTemplate.update("UPDATE travel_note SET deleted = 1, title = '已注销游记', "
                + "content = '', content_text = '' WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE note_share s "
                + "JOIN travel_note n ON s.note_id = n.id "
                + "SET s.revoked = 1, s.expire_at = NOW(), s.revoked_at = NOW() "
                + "WHERE n.user_id = ?", userId);
        jdbcTemplate.update("UPDATE expense SET deleted = 1, remark = '' WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE trip_checklist SET deleted = 1 WHERE user_id = ?", userId);
        // 反馈保留类型、状态与时间骨架，清空用户内容与管理员内部备注。
        jdbcTemplate.update("UPDATE feedback SET title = '', content = '', contact = NULL, "
                + "admin_note = NULL WHERE user_id = ?", userId);
        // 举报保留原因、状态与时间；匿名化举报人并清空该用户游记相关的快照文本。
        jdbcTemplate.update("UPDATE note_report SET reporter_user_id = NULL WHERE reporter_user_id = ?", userId);
        jdbcTemplate.update("UPDATE note_report r JOIN travel_note n ON n.id = r.note_id "
                + "SET r.note_title = '', r.note_content_text = NULL WHERE n.user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM quota_pack WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE audit_log SET detail = JSON_OBJECT('redacted', TRUE) WHERE user_id = ?",
                userId);

        auditLogService.record(userId, "ACCOUNT_DELETE", "USER", userId, Map.of("redacted", true), null);
        userStatusCache.evict(userId);
        quotaService.evictUserKeys(userId);
        return new DeleteAccountResponse(LocalDateTime.now());
    }
}
