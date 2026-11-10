package com.travelmapper.service.admin;

import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.controller.admin.dto.AdminAuditView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminAuditService {

    private final JdbcTemplate jdbcTemplate;

    public AdminAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<AdminAuditView> list(String action, Long userId, String actorRole, String targetType,
                                           LocalDate from, LocalDate to, PageRequest request) {
        request.validate();
        StringBuilder where = new StringBuilder(" FROM audit_log a LEFT JOIN `user` u ON u.id = a.user_id WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            where.append(" AND a.action = ?");
            params.add(action.trim().toUpperCase());
        }
        if (userId != null) {
            where.append(" AND a.user_id = ?");
            params.add(userId);
        }
        if (actorRole != null && !actorRole.isBlank()) {
            where.append(" AND a.actor_role = ?");
            params.add(actorRole.trim().toUpperCase(Locale.ROOT));
        }
        if (targetType != null && !targetType.isBlank()) {
            where.append(" AND a.target_type = ?");
            params.add(targetType.trim().toUpperCase());
        }
        if (from != null) {
            where.append(" AND a.created_at >= ?");
            params.add(from.atStartOfDay());
        }
        if (to != null) {
            where.append(" AND a.created_at < ?");
            params.add(to.plusDays(1).atStartOfDay());
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.getSize());
        pageParams.add(request.offset());
        List<AdminAuditView> rows = jdbcTemplate.query(
                "SELECT a.id, a.user_id, a.actor_role, u.username, a.action, a.target_type, a.target_id, "
                        + "a.detail, a.ip, a.created_at" + where
                        + " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    LocalDateTime created = rs.getObject("created_at", LocalDateTime.class);
                    return new AdminAuditView(
                            rs.getLong("id"),
                            rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                            rs.getString("actor_role"),
                            rs.getString("username"),
                            rs.getString("action"),
                            rs.getString("target_type"),
                            rs.getObject("target_id") == null ? null : rs.getLong("target_id"),
                            rs.getString("detail"),
                            rs.getString("ip"),
                            created);
                },
                pageParams.toArray());
        return PageResult.of(total == null ? 0 : total, request, rows);
    }
}
