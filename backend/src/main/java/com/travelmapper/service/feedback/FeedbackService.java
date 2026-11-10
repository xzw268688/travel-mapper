package com.travelmapper.service.feedback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.ratelimit.RedisRateLimiter;
import com.travelmapper.controller.admin.dto.AdminFeedbackDetailView;
import com.travelmapper.controller.admin.dto.AdminFeedbackView;
import com.travelmapper.controller.admin.dto.FeedbackStatsView;
import com.travelmapper.controller.admin.dto.UpdateFeedbackRequest;
import com.travelmapper.controller.feedback.dto.FeedbackDtos;
import com.travelmapper.repository.entity.Feedback;
import com.travelmapper.repository.enums.FeedbackStatus;
import com.travelmapper.repository.enums.FeedbackType;
import com.travelmapper.repository.mapper.FeedbackMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.travelmapper.service.note.SensitiveWordFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FeedbackService {

    private static final int DAILY_LIMIT = 5;
    private static final long DAY_SECONDS = 86_400;

    private static final String ADMIN_LIST_COLUMNS =
            "SELECT f.id, f.user_id, f.type, f.title, f.status, f.contact, "
                    + "f.created_at, f.updated_at, f.handled_at, "
                    + "u.username, u.nickname, u.status AS user_status ";

    private static final String ADMIN_DETAIL_SQL =
            "SELECT f.id, f.user_id, f.type, f.title, f.content, f.contact, f.page_path, "
                    + "f.user_agent, f.app_version, f.status, f.admin_note, f.handled_by, "
                    + "f.handled_at, f.created_at, f.updated_at, "
                    + "u.username, u.nickname, u.status AS user_status, "
                    + "h.username AS handler_username, h.nickname AS handler_nickname "
                    + "FROM feedback f "
                    + "LEFT JOIN `user` u ON u.id = f.user_id "
                    + "LEFT JOIN `user` h ON h.id = f.handled_by "
                    + "WHERE f.id = ?";

    private final FeedbackMapper feedbackMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RedisRateLimiter rateLimiter;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;

    public FeedbackService(FeedbackMapper feedbackMapper, JdbcTemplate jdbcTemplate,
                           RedisRateLimiter rateLimiter, SensitiveWordFilter sensitiveWordFilter,
                           AuditLogService auditLogService, TimeProvider timeProvider) {
        this.feedbackMapper = feedbackMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.rateLimiter = rateLimiter;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.auditLogService = auditLogService;
        this.timeProvider = timeProvider;
    }

    public FeedbackDtos.CreateResponse submit(Long userId, FeedbackDtos.CreateRequest request,
                                              String userAgent) {
        rateLimiter.check("feedback:submit:" + userId + ":" + timeProvider.today(),
                DAILY_LIMIT, DAY_SECONDS);

        LocalDateTime now = timeProvider.now();
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(request.getType());
        feedback.setTitle(truncate(sensitiveWordFilter.filter(request.getTitle().trim()), 50));
        feedback.setContent(sensitiveWordFilter.filter(request.getContent().trim()));
        feedback.setContact(trimToNull(request.getContact()));
        feedback.setPagePath(truncate(trimToNull(request.getPagePath()), 255));
        feedback.setUserAgent(truncate(trimToNull(userAgent), 512));
        feedback.setAppVersion(truncate(trimToNull(request.getAppVersion()), 32));
        feedback.setStatus(FeedbackStatus.NEW);
        feedback.setCreatedAt(now);
        feedback.setUpdatedAt(now);
        feedbackMapper.insert(feedback);
        auditLogService.record(userId, "FEEDBACK_CREATE", "FEEDBACK", feedback.getId(),
                Map.of("type", feedback.getType().name()), null);

        return new FeedbackDtos.CreateResponse(
                feedback.getId(),
                feedback.getType(),
                feedback.getTitle(),
                feedback.getContent(),
                feedback.getContact(),
                feedback.getStatus(),
                feedback.getCreatedAt());
    }

    public PageResult<FeedbackDtos.HistoryView> mine(Long userId, PageRequest request) {
        request.validate();
        Page<Feedback> page = feedbackMapper.selectPage(
                new Page<>(request.getPage(), request.getSize()),
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getUserId, userId)
                        .orderByDesc(Feedback::getCreatedAt)
                        .orderByDesc(Feedback::getId));
        List<FeedbackDtos.HistoryView> rows = page.getRecords().stream()
                .map(item -> new FeedbackDtos.HistoryView(
                        item.getId(),
                        item.getType(),
                        item.getTitle(),
                        item.getContent(),
                        item.getContact(),
                        item.getStatus(),
                        item.getCreatedAt(),
                        item.getUpdatedAt()))
                .toList();
        return PageResult.of(page.getTotal(), request, rows);
    }

    public PageResult<AdminFeedbackView> adminList(String type, String status, String keyword,
                                                   LocalDate from, LocalDate to,
                                                   PageRequest request) {
        request.validate();
        FeedbackType typeFilter = parseType(type);
        FeedbackStatus statusFilter = parseStatus(status);

        StringBuilder where = new StringBuilder(
                " FROM feedback f LEFT JOIN `user` u ON u.id = f.user_id WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (typeFilter != null) {
            where.append(" AND f.type = ?");
            params.add(typeFilter.name());
        }
        if (statusFilter != null) {
            where.append(" AND f.status = ?");
            params.add(statusFilter.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (f.title LIKE ? OR f.content LIKE ? OR f.contact LIKE ? "
                    + "OR u.username LIKE ? OR u.nickname LIKE ?)");
            for (int i = 0; i < 5; i++) {
                params.add(like);
            }
        }
        if (from != null) {
            where.append(" AND f.created_at >= ?");
            params.add(from.atStartOfDay());
        }
        if (to != null) {
            where.append(" AND f.created_at < ?");
            params.add(to.plusDays(1).atStartOfDay());
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.getSize());
        pageParams.add(request.offset());
        List<AdminFeedbackView> rows = jdbcTemplate.query(
                ADMIN_LIST_COLUMNS + where
                        + " ORDER BY f.created_at DESC, f.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AdminFeedbackView(
                        rs.getLong("id"),
                        rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                        submitterName(rs),
                        FeedbackType.valueOf(rs.getString("type")),
                        rs.getString("title"),
                        FeedbackStatus.valueOf(rs.getString("status")),
                        rs.getString("contact"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class),
                        rs.getObject("handled_at", LocalDateTime.class)),
                pageParams.toArray());
        return PageResult.of(total == null ? 0 : total, request, rows);
    }

    public FeedbackStatsView stats() {
        long newCount = 0;
        long processingCount = 0;
        long resolvedCount = 0;
        long total = 0;
        List<Map.Entry<String, Long>> rows = jdbcTemplate.query(
                "SELECT status, COUNT(*) AS cnt FROM feedback GROUP BY status",
                (rs, rowNum) -> Map.entry(rs.getString("status"), rs.getLong("cnt")));
        for (Map.Entry<String, Long> row : rows) {
            total += row.getValue();
            switch (row.getKey()) {
                case "NEW" -> newCount = row.getValue();
                case "PROCESSING" -> processingCount = row.getValue();
                case "RESOLVED" -> resolvedCount = row.getValue();
                default -> {
                    // 未知状态只计入总数，不进入三态卡片。
                }
            }
        }
        return new FeedbackStatsView(total, newCount, processingCount, resolvedCount);
    }

    public AdminFeedbackDetailView detail(Long id) {
        List<AdminFeedbackDetailView> rows = jdbcTemplate.query(
                ADMIN_DETAIL_SQL, (rs, rowNum) -> toDetailView(rs), id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND);
        }
        return rows.get(0);
    }

    @Transactional
    public AdminFeedbackDetailView update(Long adminId, Long id, UpdateFeedbackRequest request) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND);
        }
        boolean statusProvided = request.getStatus() != null;
        boolean noteProvided = request.getAdminNote() != null;
        if (!statusProvided && !noteProvided) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "至少提供状态或内部备注");
        }
        if (noteProvided && request.getAdminNote().trim().length() > 1000) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "内部备注不能超过 1000 字");
        }

        FeedbackStatus oldStatus = feedback.getStatus();
        FeedbackStatus newStatus = statusProvided ? request.getStatus() : oldStatus;
        String newNote = noteProvided ? trimToNull(request.getAdminNote()) : feedback.getAdminNote();
        LocalDateTime now = timeProvider.now();

        feedbackMapper.update(null, new LambdaUpdateWrapper<Feedback>()
                .eq(Feedback::getId, id)
                .set(Feedback::getStatus, newStatus)
                .set(Feedback::getAdminNote, newNote)
                .set(Feedback::getHandledBy, adminId)
                .set(Feedback::getHandledAt, now));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("feedbackId", id);
        detail.put("oldStatus", oldStatus == null ? null : oldStatus.name());
        detail.put("newStatus", newStatus == null ? null : newStatus.name());
        detail.put("noteChanged", noteProvided);
        auditLogService.record(adminId, "FEEDBACK_UPDATE", "FEEDBACK", id, detail, null);

        return detail(id);
    }

    private AdminFeedbackDetailView toDetailView(ResultSet rs) throws SQLException {
        String handlerName = displayName(
                rs.getString("handler_username"), rs.getString("handler_nickname"), null);
        return new AdminFeedbackDetailView(
                rs.getLong("id"),
                rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                submitterName(rs),
                FeedbackType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("contact"),
                rs.getString("page_path"),
                rs.getString("user_agent"),
                rs.getString("app_version"),
                FeedbackStatus.valueOf(rs.getString("status")),
                rs.getString("admin_note"),
                rs.getObject("handled_by") == null ? null : rs.getLong("handled_by"),
                handlerName,
                rs.getObject("handled_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class));
    }

    private String submitterName(ResultSet rs) throws SQLException {
        return displayName(rs.getString("username"), rs.getString("nickname"),
                rs.getString("user_status"));
    }

    private String displayName(String username, String nickname, String userStatus) {
        if (username == null || "DELETED".equals(userStatus)) {
            return "已注销用户";
        }
        return nickname == null || nickname.isBlank() ? username : nickname;
    }

    private FeedbackType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FeedbackType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "type 必须为 FEATURE/BUG/UX/OTHER");
        }
    }

    private FeedbackStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FeedbackStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "status 必须为 NEW/PROCESSING/RESOLVED");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}