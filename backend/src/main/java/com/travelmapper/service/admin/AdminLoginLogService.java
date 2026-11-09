package com.travelmapper.service.admin;

import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.controller.admin.dto.AdminLoginLogView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminLoginLogService {

    private final JdbcTemplate jdbcTemplate;

    public AdminLoginLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<AdminLoginLogView> list(String username, Long userId, String role, Boolean success,
                                              LocalDate from, LocalDate to, PageRequest request) {
        request.validate();
        StringBuilder where = new StringBuilder(" FROM login_log l WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (username != null && !username.isBlank()) {
            where.append(" AND l.username LIKE ?");
            params.add("%" + username.trim() + "%");
        }
        if (userId != null) {
            where.append(" AND l.user_id = ?");
            params.add(userId);
        }
        if (role != null && !role.isBlank()) {
            where.append(" AND l.role = ?");
            params.add(role.trim().toUpperCase(Locale.ROOT));
        }
        if (success != null) {
            where.append(" AND l.success = ?");
            params.add(success);
        }
        if (from != null) {
            where.append(" AND l.created_at >= ?");
            params.add(from.atStartOfDay());
        }
        if (to != null) {
            where.append(" AND l.created_at < ?");
            params.add(to.plusDays(1).atStartOfDay());
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.getSize());
        pageParams.add(request.offset());
        List<AdminLoginLogView> rows = jdbcTemplate.query(
                "SELECT l.id, l.user_id, l.username, l.role, l.success, l.reason, "
                        + "l.ip, l.user_agent, l.created_at" + where
                        + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    Timestamp created = rs.getTimestamp("created_at");
                    return new AdminLoginLogView(
                            rs.getLong("id"),
                            rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                            rs.getString("username"),
                            rs.getString("role"),
                            rs.getBoolean("success"),
                            rs.getString("reason"),
                            rs.getString("ip"),
                            rs.getString("user_agent"),
                            created == null ? null : created.toLocalDateTime());
                },
                pageParams.toArray());
        return PageResult.of(total == null ? 0 : total, request, rows);
    }
}