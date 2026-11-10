package com.travelmapper;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.controller.feedback.dto.FeedbackDtos;
import com.travelmapper.controller.map.dto.MapStatsResponse;
import com.travelmapper.controller.note.dto.NoteDtos;
import com.travelmapper.repository.entity.Trip;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.FeedbackType;
import com.travelmapper.repository.enums.Visibility;
import com.travelmapper.service.feedback.FeedbackService;
import com.travelmapper.service.map.MapService;
import com.travelmapper.service.note.NoteService;
import com.travelmapper.service.retention.RetentionCleanupService;
import com.travelmapper.service.trip.TripService;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TimezoneIntegrationTest.FixedClockConfig.class)
class TimezoneIntegrationTest extends AuthTestSupport {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-09T16:30:00Z");
    private static final LocalDate BEIJING_TODAY = LocalDate.of(2026, 11, 10);
    private static final LocalDateTime BEIJING_NOW = LocalDateTime.of(2026, 11, 10, 0, 30);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, TimeProvider.BEIJING);
        }
    }

    @Autowired
    private NoteService noteService;

    @Autowired
    private TripService tripService;

    @Autowired
    private MapService mapService;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private RetentionCleanupService retentionCleanupService;

    @Test
    void noteDateFilterUsesBeijingCalendarDay() {
        User user = createVerifiedUser("tz_filter");
        long early = insertNoteAt(user.getId(), "北京零点后", BEIJING_NOW.minusMinutes(20));
        long latePrevious = insertNoteAt(user.getId(), "北京前一日", BEIJING_NOW.minusHours(1));

        var page = noteService.list(user.getId(), null, BEIJING_TODAY, BEIJING_TODAY, 1, 20);

        assertThat(page.list()).extracting(NoteDtos.ListItem::id)
                .containsExactly(early)
                .doesNotContain(latePrevious);
    }

    @Test
    void feedbackDailyLimitResetsAtBeijingMidnight() {
        User user = createVerifiedUser("tz_feedback");
        String previousDayKey = "feedback:submit:" + user.getId() + ":2026-11-09";
        String todayKey = "feedback:submit:" + user.getId() + ":2026-11-10";
        redis.delete(todayKey);
        redis.opsForValue().set(previousDayKey, "5");
        try {
            FeedbackDtos.CreateRequest request = new FeedbackDtos.CreateRequest();
            request.setType(FeedbackType.BUG);
            request.setTitle("北京时间零点测试");
            request.setContent("这段正文用于验证北京时间零点后可以重新提交反馈。");
            request.setPagePath("/map");

            FeedbackDtos.CreateResponse response = feedbackService.submit(
                    user.getId(), request, "TimezoneTest");

            assertThat(response.createdAt()).isEqualTo(BEIJING_NOW);
            assertThat(redis.opsForValue().get(todayKey)).isEqualTo("1");
        } finally {
            redis.delete(previousDayKey);
            redis.delete(todayKey);
        }
    }

    @Test
    void tripExpiryUsesBeijingTodayAtMidnight() {
        User user = createVerifiedUser("tz_trip");

        Trip endedYesterday = tripService.createForItinerary(user.getId(), "JP",
                BEIJING_TODAY.minusDays(2), BEIJING_TODAY.minusDays(1), "昨天结束");
        Trip endsToday = tripService.createForItinerary(user.getId(), "TH",
                BEIJING_TODAY.minusDays(2), BEIJING_TODAY, "今天结束");

        assertThat(tripService.detail(user.getId(), endedYesterday.getId()).expired()).isTrue();
        assertThat(tripService.detail(user.getId(), endsToday.getId()).expired()).isFalse();
    }

    @Test
    void mapStatsUsesBeijingTodayAtMidnight() {
        User user = createVerifiedUser("tz_map");
        insertItinerary(user.getId(), "JP", BEIJING_TODAY.minusDays(1));
        insertItinerary(user.getId(), "TH", BEIJING_TODAY);

        MapStatsResponse stats = mapService.stats(user.getId());

        assertThat(stats.countries()).filteredOn(item -> "JP".equals(item.countryCode()))
                .singleElement().extracting(MapStatsResponse.CountryStat::expired).isEqualTo(true);
        assertThat(stats.countries()).filteredOn(item -> "TH".equals(item.countryCode()))
                .singleElement().extracting(MapStatsResponse.CountryStat::expired).isEqualTo(false);
    }

    @Test
    void shareExpiryUsesBeijingNowAtMidnight() {
        User user = createVerifiedUser("tz_share");
        NoteDtos.CreateRequest request = new NoteDtos.CreateRequest();
        request.setTitle("分享边界");
        request.setContent("<p>北京时间零点前后的分享失效测试正文。</p>");
        request.setImages(java.util.List.of());
        request.setVideos(java.util.List.of());
        request.setCountryCode("JP");
        request.setCityCode("TYO");
        request.setVisibility(Visibility.PRIVATE);
        long noteId = noteService.create(user.getId(), request).id();
        String token = noteService.share(user.getId(), noteId, 7).token();

        jdbcTemplate.update("UPDATE note_share SET expire_at = ? WHERE token = ?",
                BEIJING_NOW.minusMinutes(1), token);
        assertThatThrownBy(() -> noteService.shareNoteForReport(token))
                .isInstanceOf(BusinessException.class);

        jdbcTemplate.update("UPDATE note_share SET expire_at = ? WHERE token = ?",
                BEIJING_NOW.plusMinutes(1), token);
        assertThat(noteService.shareNoteForReport(token).getId()).isEqualTo(noteId);
    }

    @Test
    void retentionCleanupUsesBeijingNowAtMidnight() {
        User user = createVerifiedUser("tz_retention");
        String oldAction = "TZ_OLD_" + user.getId();
        String newAction = "TZ_NEW_" + user.getId();
        jdbcTemplate.update("INSERT INTO audit_log (user_id, action, created_at) VALUES (?, ?, ?)",
                user.getId(), oldAction, BEIJING_NOW.minusDays(31));
        jdbcTemplate.update("INSERT INTO audit_log (user_id, action, created_at) VALUES (?, ?, ?)",
                user.getId(), newAction, BEIJING_NOW.minusMinutes(10));
        try {
            retentionCleanupService.cleanupAuditLogs();

            assertThat(count("audit_log", "action = ?", oldAction)).isZero();
            assertThat(count("audit_log", "action = ?", newAction)).isEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM audit_log WHERE action IN (?, ?)", oldAction, newAction);
        }
    }

    private long insertNoteAt(Long userId, String title, LocalDateTime createdAt) {
        jdbcTemplate.update("INSERT INTO travel_note "
                        + "(user_id, country_code, city_code, title, content, content_text, created_at, updated_at) "
                        + "VALUES (?, 'JP', 'TYO', ?, '<p>x</p>', 'x', ?, ?)",
                userId, title, createdAt, createdAt);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM travel_note WHERE user_id = ? AND title = ? ORDER BY id DESC LIMIT 1",
                Long.class, userId, title);
    }

    private void insertItinerary(Long userId, String countryCode, LocalDate endDate) {
        jdbcTemplate.update("INSERT INTO itinerary "
                        + "(user_id, country_code, title, start_date, end_date, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'SAVED', ?, ?)",
                userId, countryCode, "边界行程 " + countryCode,
                endDate.minusDays(1), endDate, BEIJING_NOW.minusHours(1), BEIJING_NOW.minusHours(1));
    }
}