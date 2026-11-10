package com.travelmapper.service.announcement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.controller.admin.dto.AdminAnnouncementDetailView;
import com.travelmapper.controller.admin.dto.AdminAnnouncementView;
import com.travelmapper.controller.admin.dto.AnnouncementRequest;
import com.travelmapper.controller.announcement.dto.AnnouncementUserDtos;
import com.travelmapper.repository.entity.Announcement;
import com.travelmapper.repository.entity.AnnouncementRead;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.AnnouncementStatus;
import com.travelmapper.repository.enums.AnnouncementType;
import com.travelmapper.repository.enums.Role;
import com.travelmapper.repository.enums.UserStatus;
import com.travelmapper.repository.mapper.AnnouncementMapper;
import com.travelmapper.repository.mapper.AnnouncementReadMapper;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.service.audit.AuditLogService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AnnouncementService {

    private static final int ACTIVE_LIMIT = 20;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementReadMapper readMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;
    private final TimeProvider timeProvider;

    public AnnouncementService(AnnouncementMapper announcementMapper,
                               AnnouncementReadMapper readMapper,
                               UserMapper userMapper,
                               AuditLogService auditLogService,
                               JdbcTemplate jdbcTemplate,
                               TimeProvider timeProvider) {
        this.announcementMapper = announcementMapper;
        this.readMapper = readMapper;
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
        this.timeProvider = timeProvider;
    }

    public PageResult<AdminAnnouncementView> adminList(String status, String type, String keyword,
                                                       PageRequest request) {
        request.validate();
        StringBuilder where = new StringBuilder(" FROM announcement a WHERE a.deleted = 0");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND a.status = ?");
            params.add(parseStatus(status).name());
        }
        if (type != null && !type.isBlank()) {
            where.append(" AND a.type = ?");
            params.add(parseType(type).name());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND a.title LIKE ?");
            params.add("%" + keyword.trim() + "%");
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + where, Long.class, params.toArray());
        long totalUsers = totalUsers();
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.getSize());
        pageParams.add(request.offset());
        List<AdminAnnouncementView> rows = jdbcTemplate.query(
                "SELECT a.id, a.title, a.type, a.pinned, a.status, a.publish_at, a.expire_at, "
                        + "a.created_at, a.updated_at, "
                        + "(SELECT COUNT(*) FROM announcement_read r WHERE r.announcement_id = a.id) "
                        + "AS read_count" + where
                        + " ORDER BY a.pinned DESC, a.created_at DESC, a.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    long readCount = rs.getLong("read_count");
                    return new AdminAnnouncementView(
                            rs.getLong("id"),
                            rs.getString("title"),
                            AnnouncementType.valueOf(rs.getString("type")),
                            rs.getBoolean("pinned"),
                            AnnouncementStatus.valueOf(rs.getString("status")),
                            toLocalDateTime(rs.getObject("publish_at") == null ? null : rs.getLong("publish_at")),
                            toLocalDateTime(rs.getObject("expire_at") == null ? null : rs.getLong("expire_at")),
                            readCount,
                            totalUsers,
                            readRate(readCount, totalUsers),
                            rs.getObject("created_at", LocalDateTime.class),
                            rs.getObject("updated_at", LocalDateTime.class));
                },
                pageParams.toArray());
        return PageResult.of(total == null ? 0 : total, request, rows);
    }

    public AdminAnnouncementDetailView adminDetail(Long id) {
        Announcement announcement = require(id);
        long readCount = readMapper.selectCount(new LambdaQueryWrapper<AnnouncementRead>()
                .eq(AnnouncementRead::getAnnouncementId, id));
        long totalUsers = totalUsers();
        return new AdminAnnouncementDetailView(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getType(),
                Boolean.TRUE.equals(announcement.getPinned()),
                announcement.getStatus(),
                toLocalDateTime(announcement.getPublishAt()),
                toLocalDateTime(announcement.getExpireAt()),
                announcement.getCreatedBy(),
                readCount,
                totalUsers,
                readRate(readCount, totalUsers),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt());
    }

    @Transactional
    public AdminAnnouncementDetailView create(Long adminId, AnnouncementRequest request) {
        validateTimes(request.getPublishAt(), request.getExpireAt());
        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle().trim());
        announcement.setContent(request.getContent());
        announcement.setType(request.getType());
        announcement.setPinned(Boolean.TRUE.equals(request.getPinned()));
        announcement.setStatus(AnnouncementStatus.DRAFT);
        announcement.setPublishAt(toEpoch(request.getPublishAt()));
        announcement.setExpireAt(toEpoch(request.getExpireAt()));
        announcement.setCreatedBy(adminId);
        announcementMapper.insert(announcement);
        audit(adminId, "ANNOUNCEMENT_CREATE", announcement.getId(),
                Map.of("type", announcement.getType().name(),
                        "pinned", announcement.getPinned()));
        return adminDetail(announcement.getId());
    }

    @Transactional
    public AdminAnnouncementDetailView update(Long adminId, Long id, AnnouncementRequest request) {
        Announcement announcement = require(id);
        validateTimes(request.getPublishAt(), request.getExpireAt());
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(announcement.getTitle(), request.getTitle().trim())) {
            announcement.setTitle(request.getTitle().trim());
            changed.add("title");
        }
        if (!Objects.equals(announcement.getContent(), request.getContent())) {
            announcement.setContent(request.getContent());
            changed.add("content");
        }
        if (announcement.getType() != request.getType()) {
            announcement.setType(request.getType());
            changed.add("type");
        }
        boolean pinned = request.getPinned() != null
                ? request.getPinned() : Boolean.TRUE.equals(announcement.getPinned());
        if (Boolean.TRUE.equals(announcement.getPinned()) != pinned) {
            announcement.setPinned(pinned);
            changed.add("pinned");
        }
        Long nextPublishAt = toEpoch(request.getPublishAt());
        Long nextExpireAt = toEpoch(request.getExpireAt());
        if (!Objects.equals(announcement.getPublishAt(), nextPublishAt)) {
            announcement.setPublishAt(nextPublishAt);
            changed.add("publishAt");
        }
        if (!Objects.equals(announcement.getExpireAt(), nextExpireAt)) {
            announcement.setExpireAt(nextExpireAt);
            changed.add("expireAt");
        }
        announcementMapper.updateById(announcement);
        audit(adminId, "ANNOUNCEMENT_UPDATE", id, Map.of("changed", changed));
        return adminDetail(id);
    }

    @Transactional
    public AdminAnnouncementDetailView publish(Long adminId, Long id) {
        Announcement announcement = require(id);
        long now = System.currentTimeMillis();
        if (announcement.getExpireAt() != null && announcement.getExpireAt() <= now) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "公告结束时间已过，不能发布");
        }
        if (announcement.getPublishAt() == null) {
            announcement.setPublishAt(now);
        }
        announcement.setStatus(AnnouncementStatus.PUBLISHED);
        announcementMapper.updateById(announcement);
        audit(adminId, "ANNOUNCEMENT_PUBLISH", id, Map.of());
        return adminDetail(id);
    }

    @Transactional
    public AdminAnnouncementDetailView offline(Long adminId, Long id) {
        Announcement announcement = require(id);
        announcement.setStatus(AnnouncementStatus.OFFLINE);
        announcementMapper.updateById(announcement);
        audit(adminId, "ANNOUNCEMENT_OFFLINE", id, Map.of());
        return adminDetail(id);
    }

    @Transactional
    public void delete(Long adminId, Long id) {
        require(id);
        announcementMapper.deleteById(id);
        audit(adminId, "ANNOUNCEMENT_DELETE", id, Map.of());
    }

    public AnnouncementUserDtos.ActiveAnnouncementsResponse activeForUser(Long userId) {
        long now = System.currentTimeMillis();
        List<Announcement> active = announcementMapper.selectList(
                new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getStatus, AnnouncementStatus.PUBLISHED)
                        .and(w -> w.isNull(Announcement::getPublishAt)
                                .or().le(Announcement::getPublishAt, now))
                        .and(w -> w.isNull(Announcement::getExpireAt)
                                .or().gt(Announcement::getExpireAt, now))
                        .orderByDesc(Announcement::getPinned)
                        .orderByDesc(Announcement::getPublishAt)
                        .orderByDesc(Announcement::getId)
                        .last("LIMIT " + ACTIVE_LIMIT));
        Set<Long> readIds = readIds(userId, active);
        List<AnnouncementUserDtos.AnnouncementView> views = active.stream()
                .map(item -> new AnnouncementUserDtos.AnnouncementView(
                        item.getId(),
                        item.getTitle(),
                        item.getContent(),
                        item.getType(),
                        Boolean.TRUE.equals(item.getPinned()),
                        toLocalDateTime(item.getPublishAt()),
                        toLocalDateTime(item.getExpireAt()),
                        readIds.contains(item.getId())))
                .toList();
        long unread = views.stream().filter(item -> !item.read()).count();
        return new AnnouncementUserDtos.ActiveAnnouncementsResponse(views.size(), unread, views);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null || !isEffective(announcement, System.currentTimeMillis())) {
            throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }
        AnnouncementRead read = new AnnouncementRead();
        read.setAnnouncementId(id);
        read.setUserId(userId);
        read.setReadAt(timeProvider.now());
        try {
            readMapper.insert(read);
        } catch (DuplicateKeyException ignored) {
            // 已读幂等：重复打开不新增记录。
        }
    }

    private Set<Long> readIds(Long userId, List<Announcement> active) {
        if (active.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = active.stream().map(Announcement::getId).toList();
        List<AnnouncementRead> reads = readMapper.selectList(
                new LambdaQueryWrapper<AnnouncementRead>()
                        .eq(AnnouncementRead::getUserId, userId)
                        .in(AnnouncementRead::getAnnouncementId, ids));
        Set<Long> result = new HashSet<>();
        for (AnnouncementRead read : reads) {
            result.add(read.getAnnouncementId());
        }
        return result;
    }

    private boolean isEffective(Announcement announcement, long now) {
        if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            return false;
        }
        if (announcement.getPublishAt() != null && announcement.getPublishAt() > now) {
            return false;
        }
        return announcement.getExpireAt() == null || announcement.getExpireAt() > now;
    }

    private Announcement require(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }
        return announcement;
    }

    private void validateTimes(LocalDateTime publishAt, LocalDateTime expireAt) {
        if (publishAt != null && expireAt != null && !expireAt.isAfter(publishAt)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
    }

    private long totalUsers() {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, Role.USER)
                .eq(User::getStatus, UserStatus.NORMAL));
        return count == null ? 0 : count;
    }

    private double readRate(long readCount, long totalUsers) {
        if (totalUsers == 0) {
            return 0;
        }
        return Math.round(readCount * 10000.0 / totalUsers) / 10000.0;
    }

    private AnnouncementStatus parseStatus(String value) {
        try {
            return AnnouncementStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "status 必须为 DRAFT/PUBLISHED/OFFLINE");
        }
    }

    private AnnouncementType parseType(String value) {
        try {
            return AnnouncementType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "type 必须为 NOTICE/ANNOUNCEMENT");
        }
    }

    private void audit(Long adminId, String action, Long id, Object detail) {
        auditLogService.record(adminId, action, "ANNOUNCEMENT", id, detail, null);
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private LocalDateTime toLocalDateTime(Long epochMs) {
        return epochMs == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZONE);
    }

    private Long toEpoch(LocalDateTime value) {
        return value == null ? null : value.atZone(ZONE).toInstant().toEpochMilli();
    }
}