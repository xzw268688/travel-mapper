package com.travelmapper.service.note;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.security.CurrentUser;
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
    private final SensitiveWordFilter sensitiveWordFilter;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.share.base-url:http://localhost:8080}")
    private String shareBaseUrl;

    public NoteService(TravelNoteMapper noteMapper, NoteShareMapper shareMapper,
                       ItineraryMapper itineraryMapper, ItineraryDayMapper itineraryDayMapper,
                       TripMapper tripMapper, UserMapper userMapper, CatalogService catalogService,
                       TripService tripService, MapService mapService,
                       StorageUrlService storageUrlService, SensitiveWordFilter sensitiveWordFilter,
                       AuditLogService auditLogService, ObjectMapper objectMapper) {
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
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NoteDtos.CreateResponse create(Long userId, NoteDtos.CreateRequest request) {
        String title = request.getTitle();
        String rawContent = request.getContent();
        if (title == null || title.isBlank() || title.length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "标题必须为 1-50 字");
        }
        if (rawContent == null || rawContent.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "正文必填");
        }
        if (rawContent.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new BusinessException(ErrorCode.CONTENT_SIZE_LIMIT);
        }
        List<String> images = normalizeImages(request.getImages());
        String cover = normalizeCover(request.getCoverImage(), images);
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
                // 未手动指定时按创建日期自动归组；旅行结束后 30 天内仍算同一趟。
                Trip matched = tripService.resolveForDate(
                        userId, countryCode, LocalDate.now(), TripService.GROUPING_WINDOW_DAYS);
                tripId = matched == null ? null : matched.getId();
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
        note.setCoverImage(cover);
        note.setVisibility(request.getVisibility() == null ? Visibility.PRIVATE : request.getVisibility());
        note.setTakedown(false);
        noteMapper.insert(note);
        return new NoteDtos.CreateResponse(note.getId(), tripId, tripInherited,
                mapService.statusFor(userId, countryCode));
    }

    public NoteDtos.Response detail(Long userId, Long id) {
        return toResponse(requireOwned(userId, id));
    }

    @Transactional
    public NoteDtos.Response update(Long userId, Long id, NoteDtos.UpdateRequest request) {
        TravelNote note = requireOwned(userId, id);
        if (Boolean.TRUE.equals(note.getTakedown())) {
            throw new BusinessException(ErrorCode.CONTENT_TAKEDOWN);
        }
        if (request.getContent() == null && request.getImages() == null
                && request.getVisibility() == null && request.getTitle() == null
                && request.getCoverImage() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "至少提交一个可更新字段");
        }
        if (request.getTitle() != null) {
            if (request.getTitle().isBlank() || request.getTitle().length() > 50) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "标题必须为 1-50 字");
            }
            note.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            if (request.getContent().getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
                throw new BusinessException(ErrorCode.CONTENT_SIZE_LIMIT);
            }
            String sanitized = sanitize(request.getContent());
            note.setContent(sanitized);
            note.setContentText(summaryOf(sanitized));
        }
        if (request.getImages() != null) {
            List<String> images = normalizeImages(request.getImages());
            note.setImagesJson(images.isEmpty() ? null : toJson(images));
            note.setCoverImage(normalizeCover(request.getCoverImage(), images));
        } else if (request.getCoverImage() != null) {
            note.setCoverImage(request.getCoverImage());
        }
        if (request.getVisibility() != null) {
            note.setVisibility(request.getVisibility());
        }
        noteMapper.updateById(note);
        return toResponse(requireOwned(userId, id));
    }

    @Transactional
    public NoteDtos.DeleteResponse delete(Long userId, Long id) {
        TravelNote note = requireOwned(userId, id);
        noteMapper.deleteById(id);
        int revoked = revokeAllShares(id);
        return new NoteDtos.DeleteResponse(id,
                mapService.statusFor(userId, note.getCountryCode()), revoked);
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
        return shareResponse(share, true);
    }

    @Transactional
    public ShareDtos.RevokeResponse revokeShare(Long userId, Long noteId) {
        TravelNote note = requireOwned(userId, noteId);
        int revoked = revokeAllShares(noteId);
        note.setVisibility(Visibility.PRIVATE);
        noteMapper.updateById(note);
        return new ShareDtos.RevokeResponse(revoked);
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
        Safelist safelist = Safelist.relaxed().preserveRelativeLinks(true);
        String clean = Jsoup.clean(html, SANITIZE_BASE_URI, safelist);
        return sensitiveWordFilter.filter(clean);
    }

    private String summaryOf(String html) {
        String text = Jsoup.parse(html).text();
        return text.length() > 600 ? text.substring(0, 600) : text;
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
