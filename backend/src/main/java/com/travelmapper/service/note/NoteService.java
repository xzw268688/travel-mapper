package com.travelmapper.service.note;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.admin.dto.AdminNoteDetailView;
import com.travelmapper.controller.admin.dto.AdminNoteView;
import com.travelmapper.controller.note.dto.NoteDtos;
import com.travelmapper.controller.note.dto.ShareDtos;
import com.travelmapper.repository.entity.City;
import com.travelmapper.repository.entity.Itinerary;
import com.travelmapper.repository.entity.ItineraryDay;
import com.travelmapper.repository.entity.NoteShare;
import com.travelmapper.repository.entity.TravelNote;
import com.travelmapper.repository.entity.Trip;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.EnabledStatus;
import com.travelmapper.repository.enums.UserStatus;
import com.travelmapper.repository.enums.Visibility;
import com.travelmapper.repository.mapper.ItineraryDayMapper;
import com.travelmapper.repository.mapper.ItineraryMapper;
import com.travelmapper.repository.mapper.NoteShareMapper;
import com.travelmapper.repository.mapper.TravelNoteMapper;
import com.travelmapper.repository.mapper.TripMapper;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.travelmapper.service.catalog.CatalogService;
import com.travelmapper.service.map.MapService;
import com.travelmapper.service.storage.StorageUrlService;
import com.travelmapper.service.trip.TripService;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class NoteService {

    private static final int MAX_CONTENT_BYTES = 200 * 1024;
    /** 仅用于 Jsoup 解析相对链接，不写入存储，也不会被访问。 */
    private static final String SANITIZE_BASE_URI = "https://travelmapper.invalid/";
    private static final int MAX_IMAGES = 20;
    private static final int MAX_VIDEOS = 3;
    private static final int MAX_SHARE_IMAGES = 6;

    private final TravelNoteMapper noteMapper;
    private final NoteShareMapper shareMapper;
    private final ItineraryMapper itineraryMapper;
    private final ItineraryDayMapper itineraryDayMapper;
    private final TripMapper tripMapper;
    private final UserMapper userMapper;
    private final CatalogService catalogService;
    private final TripService tripService;
    private final MapService mapService;
    private final StorageUrlService storageUrlService;
    private final NoteMediaValidator mediaValidator;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.share.base-url:http://localhost:8080}")
    private String shareBaseUrl;

    public NoteService(TravelNoteMapper noteMapper, NoteShareMapper shareMapper,
                       ItineraryMapper itineraryMapper, ItineraryDayMapper itineraryDayMapper,
                       TripMapper tripMapper, UserMapper userMapper, CatalogService catalogService,
                       TripService tripService, MapService mapService,
                       StorageUrlService storageUrlService, NoteMediaValidator mediaValidator,
                       SensitiveWordFilter sensitiveWordFilter,
                       AuditLogService auditLogService, ObjectMapper objectMapper,
                       JdbcTemplate jdbcTemplate) {
        this.noteMapper = noteMapper;
        this.shareMapper = shareMapper;
        this.itineraryMapper = itineraryMapper;
        this.itineraryDayMapper = itineraryDayMapper;
        this.tripMapper = tripMapper;
        this.userMapper = userMapper;
        this.catalogService = catalogService;
        this.tripService = tripService;
        this.mapService = mapService;
        this.storageUrlService = storageUrlService;
        this.mediaValidator = mediaValidator;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public NoteDtos.CreateResponse create(Long userId, NoteDtos.CreateRequest request) {
        String rawTitle = request.getTitle();
        String rawContent = request.getContent();
        if (rawTitle == null || rawTitle.isBlank() || rawTitle.length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "标题必须为 1-50 字");
        }
        String title = normalizeTitle(rawTitle);
        if (rawContent == null || rawContent.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "正文必填");
        }
        if (rawContent.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new BusinessException(ErrorCode.CONTENT_SIZE_LIMIT);
        }
        List<String> images = normalizeImages(request.getImages());
        List<String> videos = normalizeVideos(request.getVideos());
        String cover = normalizeCover(request.getCoverImage(), images);
        mediaValidator.validate(userId, images, videos);
        String countryCode;
        String cityCode;
        Long tripId;
        boolean tripInherited = false;

        if (request.getItineraryId() != null) {
            Itinerary itinerary = itineraryMapper.selectById(request.getItineraryId());
            if (itinerary == null || !userId.equals(itinerary.getUserId())) {
                throw new BusinessException(ErrorCode.ITINERARY_NOT_FOUND);
            }
            countryCode = itinerary.getCountryCode();
            List<ItineraryDay> days = itineraryDayMapper.selectByItinerary(itinerary.getId());
            cityCode = days.isEmpty() ? request.getCityCode().toUpperCase(Locale.ROOT)
                    : days.get(0).getCityCode();
            catalogService.requireEnabledCity(countryCode, cityCode);
            tripId = itinerary.getTripId();
            tripInherited = tripId != null;
        } else {
            countryCode = request.getCountryCode().toUpperCase(Locale.ROOT);
            cityCode = request.getCityCode().toUpperCase(Locale.ROOT);
            catalogService.requireEnabledCity(countryCode, cityCode);
            tripId = request.getTripId();
            if (tripId != null) {
                Trip trip = tripService.requireOwnedTrip(userId, tripId);
                if (!trip.getCountryCode().equals(countryCode)) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "旅行国家与游记不一致");
                }
            } else {
                // 未指定时按创建日期自动匹配归档；没有归档就新建，保证不会留在未归组。
                Trip matched = tripService.resolveOrCreateForDate(
                        userId, countryCode, LocalDate.now());
                tripId = matched.getId();
            }
        }

        String sanitized = sanitize(rawContent);
        if (sanitized.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new BusinessException(ErrorCode.CONTENT_SIZE_LIMIT);
        }
        TravelNote note = new TravelNote();
        note.setUserId(userId);
        note.setTripId(tripId);
        note.setCountryCode(countryCode);
        note.setCityCode(cityCode);
        note.setTitle(title);
        note.setContent(sanitized);
        note.setContentText(summaryOf(sanitized));
        note.setImagesJson(images.isEmpty() ? null : toJson(images));
        note.setVideosJson(videos.isEmpty() ? null : toJson(videos));
        note.setCoverImage(cover);
        note.setVisibility(request.getVisibility() == null ? Visibility.PRIVATE : request.getVisibility());
        note.setTakedown(false);
        noteMapper.insert(note);
        auditLogService.record(userId, "NOTE_CREATE", "NOTE", note.getId(),
                Map.of("visibility", note.getVisibility().name(),
                        "imageCount", images.size(), "videoCount", videos.size()), null);
        return new NoteDtos.CreateResponse(note.getId(), tripId, tripInherited,
                mapService.statusFor(userId, countryCode));
    }

    public NoteDtos.Response detail(Long userId, Long id) {
        return toResponse(requireOwned(userId, id));
    }

    /** 本人游记列表：关键字匹配标题与正文摘要，日期按创建时间筛选。 */
    public PageResult<NoteDtos.ListItem> list(Long userId, String keyword,
                                              LocalDate from, LocalDate to,
                                              int page, int size) {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(page);
        pageRequest.setSize(size);
        pageRequest.validate();

        LambdaQueryWrapper<TravelNote> query = new LambdaQueryWrapper<TravelNote>()
                .eq(TravelNote::getUserId, userId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(TravelNote::getTitle, value)
                    .or()
                    .like(TravelNote::getContentText, value));
        }
        if (from != null) {
            query.ge(TravelNote::getCreatedAt, from.atStartOfDay());
        }
        if (to != null) {
            query.lt(TravelNote::getCreatedAt, to.plusDays(1).atStartOfDay());
        }
        query.orderByDesc(TravelNote::getCreatedAt).orderByDesc(TravelNote::getId);

        Page<TravelNote> result = noteMapper.selectPage(new Page<>(page, size), query);
        List<NoteDtos.ListItem> items = new ArrayList<>();
        for (TravelNote note : result.getRecords()) {
            items.add(new NoteDtos.ListItem(
                    note.getId(),
                    note.getTitle(),
                    summary(note.getContentText(), 80),
                    note.getCoverImage() == null ? null : signUrl(note.getCoverImage(), 600),
                    note.getCountryCode(),
                    catalogService.countryName(note.getCountryCode()),
                    note.getCityCode(),
                    catalogService.cityName(note.getCountryCode(), note.getCityCode()),
                    note.getVisibility().name(),
                    Boolean.TRUE.equals(note.getTakedown()),
                    note.getCreatedAt(),
                    note.getUpdatedAt()));
        }
        return PageResult.of(result.getTotal(), pageRequest, items);
    }

    private String summary(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.substring(0, Math.min(maxLength, text.length()));
    }

    @Transactional
    public NoteDtos.Response update(Long userId, Long id, NoteDtos.UpdateRequest request) {
        TravelNote note = requireOwned(userId, id);
        if (Boolean.TRUE.equals(note.getTakedown())) {
            throw new BusinessException(ErrorCode.CONTENT_TAKEDOWN);
        }
        if (request.getContent() == null && request.getImages() == null
                && request.getVideos() == null
                && request.getVisibility() == null && request.getTitle() == null
                && request.getCoverImage() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "至少提交一个可更新字段");
        }
        List<String> normalizedImages = request.getImages() == null
                ? null : normalizeImages(request.getImages());
        List<String> normalizedVideos = request.getVideos() == null
                ? null : normalizeVideos(request.getVideos());
        if (request.getContent() != null || normalizedImages != null || normalizedVideos != null) {
            List<String> effectiveImages = normalizedImages != null
                    ? normalizedImages : parseImages(note.getImagesJson());
            List<String> effectiveVideos = normalizedVideos != null
                    ? normalizedVideos : parseVideos(note.getVideosJson());
            mediaValidator.validate(userId, effectiveImages, effectiveVideos);
        }
        List<String> changed = new ArrayList<>();
        if (request.getTitle() != null) {
            if (request.getTitle().isBlank() || request.getTitle().length() > 50) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "标题必须为 1-50 字");
            }
            note.setTitle(normalizeTitle(request.getTitle()));
            changed.add("title");
        }
        if (request.getContent() != null) {
            if (request.getContent().getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
                throw new BusinessException(ErrorCode.CONTENT_SIZE_LIMIT);
            }
            String sanitized = sanitize(request.getContent());
            note.setContent(sanitized);
            note.setContentText(summaryOf(sanitized));
            changed.add("content");
        }
        if (normalizedImages != null) {
            note.setImagesJson(normalizedImages.isEmpty() ? null : toJson(normalizedImages));
            note.setCoverImage(normalizeCover(request.getCoverImage(), normalizedImages));
            changed.add("images");
        } else if (request.getCoverImage() != null) {
            note.setCoverImage(normalizeCover(request.getCoverImage(),
                    parseImages(note.getImagesJson())));
        }
        if (normalizedVideos != null) {
            note.setVideosJson(normalizedVideos.isEmpty() ? null : toJson(normalizedVideos));
            changed.add("videos");
        }
        if (request.getCoverImage() != null) {
            changed.add("cover");
        }
        if (request.getVisibility() != null) {
            note.setVisibility(request.getVisibility());
            changed.add("visibility");
        }
        noteMapper.updateById(note);
        auditLogService.record(userId, "NOTE_UPDATE", "NOTE", id,
                Map.of("changed", changed), null);
        return toResponse(requireOwned(userId, id));
    }

    @Transactional
    public NoteDtos.DeleteResponse delete(Long userId, Long id) {
        TravelNote note = requireOwned(userId, id);
        noteMapper.deleteById(id);
        int revoked = revokeAllShares(id);
        boolean tripDeleted = tripService.deleteIfEmpty(note.getTripId());
        auditLogService.record(userId, "NOTE_DELETE", "NOTE", id,
                Map.of("revokedShares", revoked, "tripDeleted", tripDeleted), null);
        return new NoteDtos.DeleteResponse(id,
                mapService.statusFor(userId, note.getCountryCode()), revoked, tripDeleted);
    }

    @Transactional
    public ShareDtos.ShareResponse share(Long userId, Long noteId, Integer expireDays) {
        TravelNote note = requireOwned(userId, noteId);
        if (Boolean.TRUE.equals(note.getTakedown())) {
            throw new BusinessException(ErrorCode.CONTENT_TAKEDOWN);
        }
        int days = expireDays == null ? 7 : expireDays;
        if (days != 0 && days != 7 && days != 30) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "expireDays 仅支持 7、30、0");
        }
        NoteShare active = activeShare(noteId);
        if (active != null) {
            return shareResponse(active, false);
        }
        NoteShare share = new NoteShare();
        share.setNoteId(noteId);
        share.setToken(generateToken());
        share.setExpireAt(days == 0 ? null : LocalDateTime.now().plusDays(days));
        share.setRevoked(false);
        shareMapper.insert(share);
        note.setVisibility(Visibility.SHARED);
        noteMapper.updateById(note);
        auditLogService.record(userId, "NOTE_SHARE", "NOTE", noteId,
                Map.of("expireDays", days), null);
        return shareResponse(share, true);
    }

    @Transactional
    public ShareDtos.RevokeResponse revokeShare(Long userId, Long noteId) {
        TravelNote note = requireOwned(userId, noteId);
        int revoked = revokeAllShares(noteId);
        note.setVisibility(Visibility.PRIVATE);
        noteMapper.updateById(note);
        auditLogService.record(userId, "NOTE_UNSHARE", "NOTE", noteId,
                Map.of("revokedShares", revoked), null);
        return new ShareDtos.RevokeResponse(revoked);
    }

    /** 举报前复用分享页的失效判定，返回当前可举报的游记。 */
    public TravelNote shareNoteForReport(String token) {
        NoteShare share = shareMapper.selectOne(new LambdaQueryWrapper<NoteShare>()
                .eq(NoteShare::getToken, token));
        if (share == null || Boolean.TRUE.equals(share.getRevoked())
                || (share.getExpireAt() != null && share.getExpireAt().isBefore(LocalDateTime.now()))) {
            throw new BusinessException(ErrorCode.SHARE_INVALID);
        }
        TravelNote note = noteMapper.selectById(share.getNoteId());
        if (note == null) {
            throw new BusinessException(ErrorCode.SHARE_INVALID);
        }
        User user = userMapper.selectByIdIncludingDeleted(note.getUserId());
        if (user == null || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.SHARE_INVALID);
        }
        if (Boolean.TRUE.equals(note.getTakedown())) {
            throw new BusinessException(ErrorCode.SHARE_INVALID);
        }
        return note;
    }

    public ShareDtos.SharePageResponse publicPage(String token) {
        NoteShare share = shareMapper.selectOne(new LambdaQueryWrapper<NoteShare>()
                .eq(NoteShare::getToken, token));
        if (share == null || Boolean.TRUE.equals(share.getRevoked())
                || (share.getExpireAt() != null && share.getExpireAt().isBefore(LocalDateTime.now()))) {
            throw new BusinessException(ErrorCode.SHARE_INVALID);
        }
        TravelNote note = noteMapper.selectById(share.getNoteId());
        if (note == null) {
            throw new BusinessException(ErrorCode.SHARE_INVALID);
        }
        User user = userMapper.selectByIdIncludingDeleted(note.getUserId());
        if (user == null || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.SHARE_INVALID);
        }
        if (Boolean.TRUE.equals(note.getTakedown())) {
            throw new BusinessException(ErrorCode.SHARE_INVALID);
        }
        List<String> keys = parseImages(note.getImagesJson());
        List<String> images = new ArrayList<>();
        for (int i = 0; i < keys.size() && i < MAX_SHARE_IMAGES; i++) {
            String url = shareUrl(keys.get(i), share.getExpireAt());
            if (url != null) {
                images.add(url);
            }
        }
        String cover = note.getCoverImage() == null ? null : shareUrl(note.getCoverImage(), share.getExpireAt());
        String summary = note.getContentText() == null ? ""
                : note.getContentText().substring(0, Math.min(200, note.getContentText().length()));
        return new ShareDtos.SharePageResponse(
                note.getTitle(),
                summary,
                cover,
                images,
                note.getCountryCode(),
                catalogService.countryName(note.getCountryCode()),
                note.getCityCode(),
                catalogService.cityName(note.getCountryCode(), note.getCityCode()),
                note.getCreatedAt(),
                maskNickname(user.getNickname()));
    }

    /** 管理端内容列表：只返回元信息，不返回正文。 */
    public PageResult<AdminNoteView> adminList(String keyword, String status,
                                               LocalDate from, LocalDate to,
                                               PageRequest request) {
        request.validate();
        StringBuilder where = new StringBuilder(
                " FROM travel_note n LEFT JOIN `user` u ON u.id = n.user_id WHERE n.deleted = 0");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (n.title LIKE ? OR u.username LIKE ? OR u.nickname LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if ("TAKEDOWN".equals(normalized)) {
                where.append(" AND n.takedown = 1");
            } else if ("NORMAL".equals(normalized)) {
                where.append(" AND n.takedown = 0");
            } else {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "status 必须为 TAKEDOWN 或 NORMAL");
            }
        }
        if (from != null) {
            where.append(" AND n.created_at >= ?");
            params.add(from.atStartOfDay());
        }
        if (to != null) {
            where.append(" AND n.created_at < ?");
            params.add(to.plusDays(1).atStartOfDay());
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.getSize());
        pageParams.add(request.offset());
        List<AdminNoteView> rows = jdbcTemplate.query(
                "SELECT n.id, n.title, n.user_id, n.country_code, n.city_code, n.visibility, "
                        + "n.takedown, n.takedown_reason, n.created_at, n.updated_at, "
                        + "u.username, u.nickname" + where
                        + " ORDER BY n.created_at DESC, n.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    String countryCode = rs.getString("country_code");
                    String cityCode = rs.getString("city_code");
                    String nickname = rs.getString("nickname");
                    String username = rs.getString("username");
                    Long authorId = rs.getObject("user_id") == null ? null : rs.getLong("user_id");
                    return new AdminNoteView(
                            rs.getLong("id"),
                            rs.getString("title"),
                            authorId,
                            nickname == null || nickname.isBlank() ? username : nickname,
                            countryCode,
                            catalogService.countryName(countryCode),
                            cityCode,
                            catalogService.cityName(countryCode, cityCode),
                            rs.getString("visibility"),
                            rs.getInt("takedown") == 1,
                            rs.getString("takedown_reason"),
                            rs.getTimestamp("created_at") == null ? null
                                    : rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getTimestamp("updated_at") == null ? null
                                    : rs.getTimestamp("updated_at").toLocalDateTime());
                },
                pageParams.toArray());
        return PageResult.of(total == null ? 0 : total, request, rows);
    }

    /** 管理员查看任意游记完整内容，包含私密、已下架与软删除记录。 */
    public AdminNoteDetailView adminDetail(Long noteId) {
        TravelNote note = noteMapper.selectByIdIncludingDeleted(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOTE_NOT_FOUND);
        }
        User author = userMapper.selectByIdIncludingDeleted(note.getUserId());
        String authorName;
        if (author == null || author.getStatus() == UserStatus.DELETED) {
            authorName = "已注销用户";
        } else {
            authorName = author.getNickname() == null || author.getNickname().isBlank()
                    ? author.getUsername() : author.getNickname();
        }

        List<NoteDtos.ImageView> images = new ArrayList<>();
        for (String key : parseImages(note.getImagesJson())) {
            images.add(new NoteDtos.ImageView(key, signUrl(key, 600)));
        }
        List<NoteDtos.VideoView> videos = new ArrayList<>();
        for (String key : parseVideos(note.getVideosJson())) {
            videos.add(new NoteDtos.VideoView(key, signUrl(key, 600)));
        }
        NoteShare active = activeShare(note.getId());
        City city = catalogService.findCity(note.getCountryCode(), note.getCityCode());
        AdminNoteDetailView view = new AdminNoteDetailView(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getContentText(),
                images,
                videos,
                note.getCoverImage() == null ? null : signUrl(note.getCoverImage(), 600),
                note.getUserId(),
                authorName,
                note.getCountryCode(),
                catalogService.countryName(note.getCountryCode()),
                note.getCityCode(),
                city == null ? note.getCityCode() : city.getName(),
                note.getVisibility() == null ? null : note.getVisibility().name(),
                active != null,
                active == null ? null : active.getExpireAt(),
                Boolean.TRUE.equals(note.getTakedown()),
                note.getTakedownReason(),
                note.getTakedownAt(),
                note.getDeleted() != null && note.getDeleted() == 1,
                note.getCreatedAt(),
                note.getUpdatedAt());
        return view;
    }

    @Transactional
    public NoteDtos.TakedownResponse adminTakedown(Long adminId, Long noteId, String reason) {
        TravelNote note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOTE_NOT_FOUND);
        }
        note.setTakedown(true);
        note.setTakedownReason(reason);
        note.setTakedownAt(LocalDateTime.now());
        note.setTakedownBy(adminId);
        noteMapper.updateById(note);
        int revoked = revokeAllShares(noteId);
        auditLogService.record(adminId, "NOTE_TAKEDOWN", "NOTE", noteId,
                Map.of("reason", reason == null ? "" : reason), null);
        return new NoteDtos.TakedownResponse(noteId, true, revoked);
    }

    @Transactional
    public NoteDtos.RestoreResponse adminRestore(Long adminId, Long noteId) {
        TravelNote note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOTE_NOT_FOUND);
        }
        note.setTakedown(false);
        note.setTakedownReason(null);
        note.setTakedownAt(null);
        note.setTakedownBy(null);
        noteMapper.updateById(note);
        auditLogService.record(adminId, "NOTE_RESTORE", "NOTE", noteId, Map.of(), null);
        return new NoteDtos.RestoreResponse(noteId, false);
    }

    private NoteDtos.Response toResponse(TravelNote note) {
        List<String> keys = parseImages(note.getImagesJson());
        List<NoteDtos.ImageView> images = new ArrayList<>();
        for (String key : keys) {
            images.add(new NoteDtos.ImageView(key, signUrl(key, 600)));
        }
        List<NoteDtos.VideoView> videos = new ArrayList<>();
        for (String key : parseVideos(note.getVideosJson())) {
            videos.add(new NoteDtos.VideoView(key, signUrl(key, 600)));
        }
        NoteShare active = activeShare(note.getId());
        NoteDtos.ShareState shareState = active == null
                ? new NoteDtos.ShareState(false, null, null, null)
                : new NoteDtos.ShareState(true, active.getToken(), "/s/" + active.getToken(),
                active.getExpireAt());
        City city = catalogService.findCity(note.getCountryCode(), note.getCityCode());
        boolean cityDisabled = city == null || city.getStatus() != EnabledStatus.ENABLED;
        Trip trip = note.getTripId() == null ? null : tripMapper.selectById(note.getTripId());
        return new NoteDtos.Response(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                images,
                videos,
                note.getCoverImage(),
                note.getCountryCode(),
                catalogService.countryName(note.getCountryCode()),
                note.getCityCode(),
                city == null ? note.getCityCode() : city.getName(),
                cityDisabled,
                null,
                note.getTripId(),
                trip == null ? null : trip.getName(),
                note.getVisibility().name(),
                shareState,
                Boolean.TRUE.equals(note.getTakedown()),
                note.getTakedownReason(),
                note.getTakedownAt(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }

    private TravelNote requireOwned(Long userId, Long id) {
        TravelNote note = noteMapper.selectById(id);
        if (note == null || !userId.equals(note.getUserId())) {
            throw new BusinessException(ErrorCode.NOTE_NOT_FOUND);
        }
        return note;
    }

    private NoteShare activeShare(Long noteId) {
        return shareMapper.selectOne(new LambdaQueryWrapper<NoteShare>()
                .eq(NoteShare::getNoteId, noteId)
                .eq(NoteShare::getRevoked, false)
                .and(wrapper -> wrapper.isNull(NoteShare::getExpireAt)
                        .or().gt(NoteShare::getExpireAt, LocalDateTime.now()))
                .orderByDesc(NoteShare::getId)
                .last("LIMIT 1"));
    }

    private int revokeAllShares(Long noteId) {
        return shareMapper.update(null, new LambdaUpdateWrapper<NoteShare>()
                .eq(NoteShare::getNoteId, noteId)
                .eq(NoteShare::getRevoked, false)
                .set(NoteShare::getRevoked, true)
                .set(NoteShare::getRevokedAt, LocalDateTime.now()));
    }

    private ShareDtos.ShareResponse shareResponse(NoteShare share, boolean created) {
        String url = "/s/" + share.getToken();
        return new ShareDtos.ShareResponse(share.getToken(), url,
                shareBaseUrl + url, share.getExpireAt(), created);
    }

    private String generateToken() {
        for (int attempt = 0; attempt < 5; attempt++) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            String token = "s_" + HexFormat.of().formatHex(bytes);
            if (shareMapper.selectCount(new LambdaQueryWrapper<NoteShare>()
                    .eq(NoteShare::getToken, token)) == 0) {
                return token;
            }
        }
        throw new BusinessException(ErrorCode.SHARE_CREATE_FAILED);
    }

    private List<String> normalizeImages(List<String> images) {
        if (images == null) {
            return List.of();
        }
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.IMAGE_COUNT_LIMIT);
        }
        List<String> result = new ArrayList<>();
        for (String image : images) {
            if (image == null || image.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "图片 objectKey 不能为空");
            }
            result.add(image);
        }
        return result;
    }

    private List<String> normalizeVideos(List<String> videos) {
        if (videos == null) {
            return List.of();
        }
        if (videos.size() > MAX_VIDEOS) {
            throw new BusinessException(ErrorCode.VIDEO_COUNT_LIMIT);
        }
        List<String> result = new ArrayList<>();
        for (String video : videos) {
            if (video == null || video.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "视频 objectKey 不能为空");
            }
            result.add(video);
        }
        return result;
    }

    private String normalizeCover(String cover, List<String> images) {
        if (cover != null && !cover.isBlank()) {
            if (!images.contains(cover)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "封面必须是图片之一");
            }
            return cover;
        }
        return images.isEmpty() ? null : images.get(0);
    }

    private String sanitize(String html) {
        // 正文图片使用相对路径 /objects/{objectKey} 持久化，需保留相对链接。
        // Jsoup 只有在提供 baseUri 时才会校验并保留相对链接；输出仍保持相对形式，
        // javascript: 等危险协议依旧被 relaxed 白名单移除。
        Safelist safelist = Safelist.relaxed().preserveRelativeLinks(true)
                .addTags("video")
                .addAttributes("video", "src", "controls", "preload", "poster",
                        "width", "height", "playsinline")
                .addProtocols("video", "src", "http", "https");
        String clean = Jsoup.clean(html, SANITIZE_BASE_URI, safelist);
        return sensitiveWordFilter.filter(clean);
    }

    private String summaryOf(String html) {
        String text = Jsoup.parse(html).text();
        return text.length() > 600 ? text.substring(0, 600) : text;
    }

    private String normalizeTitle(String title) {
        String filtered = sensitiveWordFilter.filter(title.trim());
        return filtered.length() > 50 ? filtered.substring(0, 50) : filtered;
    }

    private List<String> parseImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> parseVideos(String json) {
        return parseImages(json);
    }

    private String signUrl(String key, long ttlSeconds) {
        try {
            return storageUrlService.presignDownload(key); // 600s normal access
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String shareUrl(String key, LocalDateTime tokenExpireAt) {
        try {
            return storageUrlService.presignShareDownload(key,
                    tokenExpireAt == null ? null : tokenExpireAt.atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String maskNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "***";
        }
        return nickname.substring(0, 1) + "*";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
