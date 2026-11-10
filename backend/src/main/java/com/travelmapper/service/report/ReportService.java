package com.travelmapper.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.ratelimit.RedisRateLimiter;
import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.controller.admin.dto.AcceptReportRequest;
import com.travelmapper.controller.admin.dto.AdminReportDetailView;
import com.travelmapper.controller.admin.dto.AdminReportView;
import com.travelmapper.controller.admin.dto.ReportStatsView;
import com.travelmapper.controller.note.dto.ReportDtos;
import com.travelmapper.repository.entity.NoteReport;
import com.travelmapper.repository.entity.TravelNote;
import com.travelmapper.repository.enums.ReportReason;
import com.travelmapper.repository.enums.ReportStatus;
import com.travelmapper.repository.mapper.NoteReportMapper;
import com.travelmapper.repository.mapper.TravelNoteMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.travelmapper.service.note.NoteService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportService {

    private static final long ANONYMOUS_DAILY_LIMIT = 5;
    private static final long DAY_SECONDS = 86_400;
    private static final int SNAPSHOT_MAX_CHARS = 600;

    private static final String LIST_COLUMNS =
            "SELECT r.id, r.note_id, r.reporter_user_id, r.reason, r.status, r.description, r.note_title, "
                    + "r.created_at, r.handled_at, n.title AS current_title, "
                    + "author.username AS author_username, author.nickname AS author_nickname, "
                    + "author.status AS author_status, "
                    + "reporter.username AS reporter_username, reporter.nickname AS reporter_nickname, "
                    + "reporter.status AS reporter_status ";

    private static final String LIST_FROM =
            " FROM note_report r "
                    + "LEFT JOIN travel_note n ON n.id = r.note_id "
                    + "LEFT JOIN `user` author ON author.id = n.user_id "
                    + "LEFT JOIN `user` reporter ON reporter.id = r.reporter_user_id "
                    + "WHERE 1 = 1";

    private static final String DETAIL_SQL =
            "SELECT r.*, n.title AS current_title, n.visibility AS current_visibility, "
                    + "n.updated_at AS current_updated_at, n.takedown AS current_takedown, "
                    + "n.deleted AS current_deleted, n.user_id AS author_id, "
                    + "author.username AS author_username, author.nickname AS author_nickname, "
                    + "author.status AS author_status, "
                    + "reporter.username AS reporter_username, reporter.nickname AS reporter_nickname, "
                    + "reporter.status AS reporter_status, "
                    + "handler.username AS handler_username, handler.nickname AS handler_nickname "
                    + "FROM note_report r "
                    + "LEFT JOIN travel_note n ON n.id = r.note_id "
                    + "LEFT JOIN `user` author ON author.id = n.user_id "
                    + "LEFT JOIN `user` reporter ON reporter.id = r.reporter_user_id "
                    + "LEFT JOIN `user` handler ON handler.id = r.handled_by "
                    + "WHERE r.id = ?";

    private final NoteReportMapper reportMapper;
    private final TravelNoteMapper noteMapper;
    private final NoteService noteService;
    private final RedisRateLimiter rateLimiter;
    private final StringRedisTemplate redis;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;
    private final TimeProvider timeProvider;

    public ReportService(NoteReportMapper reportMapper, TravelNoteMapper noteMapper,
                         NoteService noteService, RedisRateLimiter rateLimiter,
                         StringRedisTemplate redis, AuditLogService auditLogService,
                         JdbcTemplate jdbcTemplate, TimeProvider timeProvider) {
        this.reportMapper = reportMapper;
        this.noteMapper = noteMapper;
        this.noteService = noteService;
        this.rateLimiter = rateLimiter;
        this.redis = redis;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
        this.timeProvider = timeProvider;
    }

    public ReportDtos.Receipt submit(String token, String clientIp, Long reporterUserId,
                                     ReportDtos.CreateRequest request) {
        TravelNote note = noteService.shareNoteForReport(token);
        if (reporterUserId != null && reporterUserId.equals(note.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不能举报自己的游记");
        }
        String description = trimToNull(request.getDescription());
        if (request.getReason() == ReportReason.OTHER && description == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "选择「其他」时必须填写说明");
        }
        if (description != null && description.length() > 200) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "举报说明不能超过 200 字");
        }

        if (reporterUserId != null) {
            NoteReport existing = reportMapper.selectOne(new LambdaQueryWrapper<NoteReport>()
                    .eq(NoteReport::getReporterUserId, reporterUserId)
                    .eq(NoteReport::getNoteId, note.getId())
                    .eq(NoteReport::getStatus, ReportStatus.PENDING)
                    .orderByDesc(NoteReport::getId)
                    .last("LIMIT 1"));
            if (existing != null) {
                return new ReportDtos.Receipt(existing.getId(), true, existing.getCreatedAt());
            }
        } else {
            String hashedIp = sha256(clientIp == null || clientIp.isBlank() ? "unknown" : clientIp);
            String dedupeKey = "report:anon:" + sha256(hashedIp + ":" + token);
            Boolean fresh = redis.opsForValue().setIfAbsent(dedupeKey, "1", Duration.ofHours(24));
            if (!Boolean.TRUE.equals(fresh)) {
                return new ReportDtos.Receipt(null, true, null);
            }
            try {
                rateLimiter.check("report:ip:" + hashedIp + ":" + timeProvider.today(),
                        ANONYMOUS_DAILY_LIMIT, DAY_SECONDS);
            } catch (RuntimeException ex) {
                redis.delete(dedupeKey);
                throw ex;
            }
        }

        LocalDateTime now = timeProvider.now();
        NoteReport report = new NoteReport();
        report.setNoteId(note.getId());
        report.setReporterUserId(reporterUserId);
        report.setReason(request.getReason());
        report.setDescription(description);
        report.setNoteTitle(note.getTitle());
        report.setNoteContentText(truncate(note.getContentText(), SNAPSHOT_MAX_CHARS));
        report.setNoteUpdatedAt(note.getUpdatedAt());
        report.setStatus(ReportStatus.PENDING);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        reportMapper.insert(report);
        auditLogService.record(reporterUserId, "NOTE_REPORT", "NOTE", note.getId(),
                Map.of("reason", request.getReason().name(),
                        "anonymous", reporterUserId == null), null);
        return new ReportDtos.Receipt(report.getId(), false, now);
    }

    public PageResult<AdminReportView> list(String status, String reason, String keyword,
                                             LocalDate from, LocalDate to,
                                             PageRequest request) {
        request.validate();
        ReportStatus statusFilter = parseStatus(status);
        ReportReason reasonFilter = parseReason(reason);

        StringBuilder where = new StringBuilder(LIST_FROM);
        List<Object> params = new ArrayList<>();
        if (statusFilter != null) {
            where.append(" AND r.status = ?");
            params.add(statusFilter.name());
        }
        if (reasonFilter != null) {
            where.append(" AND r.reason = ?");
            params.add(reasonFilter.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (r.description LIKE ? OR r.note_title LIKE ? OR n.title LIKE ? "
                    + "OR author.username LIKE ? OR author.nickname LIKE ?)");
            for (int i = 0; i < 5; i++) {
                params.add(like);
            }
        }
        if (from != null) {
            where.append(" AND r.created_at >= ?");
            params.add(from.atStartOfDay());
        }
        if (to != null) {
            where.append(" AND r.created_at < ?");
            params.add(to.plusDays(1).atStartOfDay());
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.getSize());
        pageParams.add(request.offset());
        List<AdminReportView> rows = jdbcTemplate.query(
                LIST_COLUMNS + where + " ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AdminReportView(
                        rs.getLong("id"),
                        rs.getLong("note_id"),
                        reporterName(rs),
                        ReportReason.valueOf(rs.getString("reason")),
                        ReportStatus.valueOf(rs.getString("status")),
                        rs.getString("description"),
                        rs.getString("note_title"),
                        rs.getString("current_title"),
                        authorName(rs),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("handled_at", LocalDateTime.class)),
                pageParams.toArray());
        return PageResult.of(total == null ? 0 : total, request, rows);
    }

    public ReportStatsView stats() {
        long total = 0;
        long pending = 0;
        long accepted = 0;
        long rejected = 0;
        List<Map.Entry<String, Long>> rows = jdbcTemplate.query(
                "SELECT status, COUNT(*) AS cnt FROM note_report GROUP BY status",
                (rs, rowNum) -> Map.entry(rs.getString("status"), rs.getLong("cnt")));
        for (Map.Entry<String, Long> row : rows) {
            total += row.getValue();
            switch (row.getKey()) {
                case "PENDING" -> pending = row.getValue();
                case "ACCEPTED" -> accepted = row.getValue();
                case "REJECTED" -> rejected = row.getValue();
                default -> {
                    // 未知状态只计入总数。
                }
            }
        }
        return new ReportStatsView(total, pending, accepted, rejected);
    }

    public AdminReportDetailView detail(Long id) {
        List<AdminReportDetailView> rows = jdbcTemplate.query(
                DETAIL_SQL, (rs, rowNum) -> toDetailView(rs), id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        return rows.get(0);
    }

    @Transactional
    public AdminReportDetailView accept(Long adminId, Long id, AcceptReportRequest request) {
        NoteReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "举报已处理");
        }
        String reason = request.getNoteReason() == null ? null : request.getNoteReason().trim();
        if (reason == null || reason.isEmpty() || reason.length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "下架原因必须为 1-100 字");
        }

        TravelNote note = noteMapper.selectByIdIncludingDeleted(report.getNoteId());
        boolean noteDeleted = note == null || (note.getDeleted() != null && note.getDeleted() == 1);
        if (!noteDeleted) {
            noteService.adminTakedown(adminId, note.getId(), reason);
        }

        LocalDateTime now = timeProvider.now();
        reportMapper.update(null, new LambdaUpdateWrapper<NoteReport>()
                .eq(NoteReport::getNoteId, report.getNoteId())
                .eq(NoteReport::getStatus, ReportStatus.PENDING)
                .set(NoteReport::getStatus, ReportStatus.ACCEPTED)
                .set(NoteReport::getHandledBy, adminId)
                .set(NoteReport::getHandledAt, now));

        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("reportId", id);
        auditDetail.put("noteId", report.getNoteId());
        auditDetail.put("noteReason", reason);
        auditDetail.put("noteDeleted", noteDeleted);
        auditLogService.record(adminId, "REPORT_ACCEPT", "REPORT", id, auditDetail, null);

        return detail(id);
    }

    @Transactional
    public AdminReportDetailView reject(Long adminId, Long id) {
        NoteReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "举报已处理");
        }
        reportMapper.update(null, new LambdaUpdateWrapper<NoteReport>()
                .eq(NoteReport::getId, id)
                .set(NoteReport::getStatus, ReportStatus.REJECTED)
                .set(NoteReport::getHandledBy, adminId)
                .set(NoteReport::getHandledAt, timeProvider.now()));

        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("reportId", id);
        auditDetail.put("noteId", report.getNoteId());
        auditLogService.record(adminId, "REPORT_REJECT", "REPORT", id, auditDetail, null);

        return detail(id);
    }

    private AdminReportDetailView toDetailView(ResultSet rs) throws SQLException {
        Long authorId = rs.getObject("author_id") == null ? null : rs.getLong("author_id");
        long acceptedByAuthor = authorId == null ? 0 : acceptedReportCount(authorId);
        Object currentDeleted = rs.getObject("current_deleted");
        return new AdminReportDetailView(
                rs.getLong("id"),
                rs.getLong("note_id"),
                rs.getObject("reporter_user_id") == null ? null : rs.getLong("reporter_user_id"),
                reporterName(rs),
                ReportReason.valueOf(rs.getString("reason")),
                rs.getString("description"),
                rs.getString("note_title"),
                rs.getString("note_content_text"),
                rs.getObject("note_updated_at", LocalDateTime.class),
                ReportStatus.valueOf(rs.getString("status")),
                rs.getObject("handled_by") == null ? null : rs.getLong("handled_by"),
                displayName(rs.getString("handler_username"), rs.getString("handler_nickname")),
                rs.getObject("handled_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                authorId,
                authorName(rs),
                rs.getString("current_title"),
                rs.getString("current_visibility"),
                rs.getObject("current_updated_at", LocalDateTime.class),
                rs.getInt("current_takedown") == 1,
                currentDeleted != null && rs.getInt("current_deleted") == 1,
                acceptedByAuthor);
    }

    private long acceptedReportCount(Long authorId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note_report r JOIN travel_note n ON n.id = r.note_id "
                        + "WHERE n.user_id = ? AND r.status = 'ACCEPTED'",
                Long.class, authorId);
        return count == null ? 0 : count;
    }

    private String reporterName(ResultSet rs) throws SQLException {
        if (rs.getObject("reporter_user_id") == null
                || "DELETED".equals(rs.getString("reporter_status"))) {
            return "匿名访客";
        }
        String name = displayName(rs.getString("reporter_username"), rs.getString("reporter_nickname"));
        return name == null ? "匿名访客" : name;
    }

    private String authorName(ResultSet rs) throws SQLException {
        String username = rs.getString("author_username");
        if (username == null || "DELETED".equals(rs.getString("author_status"))) {
            return "已注销用户";
        }
        return displayName(username, rs.getString("author_nickname"));
    }

    private String displayName(String username, String nickname) {
        if (username == null) {
            return null;
        }
        return nickname == null || nickname.isBlank() ? username : nickname;
    }

    private ReportStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ReportStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "status 必须为 PENDING/ACCEPTED/REJECTED");
        }
    }

    private ReportReason parseReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ReportReason.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "reason 必须为 SPAM/ABUSE/PORN/ILLEGAL/PRIVACY/OTHER");
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

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}