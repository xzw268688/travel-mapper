package com.travelmapper;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.controller.trip.dto.TripStatusResponse;
import com.travelmapper.repository.entity.Itinerary;
import com.travelmapper.repository.entity.Trip;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.ItineraryStatus;
import com.travelmapper.repository.enums.TripStatus;
import com.travelmapper.repository.mapper.ItineraryMapper;
import com.travelmapper.repository.mapper.TripMapper;
import com.travelmapper.service.itinerary.ItineraryService;
import com.travelmapper.service.note.NoteService;
import com.travelmapper.service.trip.TripService;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripStatusIntegrationTest extends AuthTestSupport {

    @Autowired
    private TripService tripService;

    @Autowired
    private ItineraryService itineraryService;

    @Autowired
    private NoteService noteService;

    @Autowired
    private TripMapper tripMapper;

    @Autowired
    private ItineraryMapper itineraryMapper;

    @Test
    void derivedStatusUsesDatesAndRecords() {
        User user = createVerifiedUser("autostatus");
        LocalDate today = LocalDate.now();

        Trip preTrip = tripService.createForItinerary(user.getId(), "JP",
                today.plusDays(10), today.plusDays(12), "出发前预订");
        insertExpense(user, preTrip.getId(), "JP", today);
        assertEffective(user, preTrip.getId(), "PLANNED");

        Trip ongoing = tripService.createForItinerary(user.getId(), "TH",
                today.minusDays(1), today.plusDays(1), "旅行中");
        insertNote(user, ongoing.getId(), "TH", today.minusDays(1));
        assertEffective(user, ongoing.getId(), "IN_PROGRESS");

        Trip finished = tripService.createForItinerary(user.getId(), "KR",
                today.minusDays(10), today.minusDays(5), "已结束");
        insertNote(user, finished.getId(), "KR", today.minusDays(6));
        assertEffective(user, finished.getId(), "COMPLETED");

        Trip expired = tripService.createForItinerary(user.getId(), "SG",
                today.minusDays(10), today.minusDays(5), "只有计划");
        assertEffective(user, expired.getId(), "PLANNED");
        assertThat(tripService.detail(user.getId(), expired.getId()).expired()).isTrue();

        Trip recordOnly = insertDatelessTrip(user, "FR", "只有记录");
        insertNote(user, recordOnly.getId(), "FR", today);
        assertEffective(user, recordOnly.getId(), "COMPLETED");

        Trip futureRecord = insertDatelessTrip(user, "IT", "未来记录");
        insertExpense(user, futureRecord.getId(), "IT", today.plusDays(10));
        assertEffective(user, futureRecord.getId(), "PLANNED");
    }

    @Test
    void manualOverrideWinsUntilReset() {
        User user = createVerifiedUser("manualstatus");
        LocalDate today = LocalDate.now();
        Trip trip = tripService.createForItinerary(user.getId(), "JP",
                today.minusDays(10), today.minusDays(5), "自动完成");
        insertNote(user, trip.getId(), "JP", today.minusDays(6));
        assertEffective(user, trip.getId(), "COMPLETED");

        TripStatusResponse manualPlanned = tripService.updateStatus(user.getId(), trip.getId(), "PLANNED");
        assertThat(manualPlanned.statusManual()).isTrue();
        assertThat(manualPlanned.effectiveStatus()).isEqualTo("PLANNED");
        assertEffective(user, trip.getId(), "PLANNED");

        TripStatusResponse reset = tripService.resetStatus(user.getId(), trip.getId());
        assertThat(reset.statusManual()).isFalse();
        assertThat(reset.effectiveStatus()).isEqualTo("COMPLETED");

        Trip future = tripService.createForItinerary(user.getId(), "TH",
                today.plusDays(10), today.plusDays(12), "手动完成");
        TripStatusResponse manualCompleted = tripService.updateStatus(user.getId(), future.getId(), "COMPLETED");
        assertThat(manualCompleted.effectiveStatus()).isEqualTo("COMPLETED");
        assertThat(tripService.resetStatus(user.getId(), future.getId()).effectiveStatus()).isEqualTo("PLANNED");
    }

    @Test
    void lastContentDeletionRemovesEmptyTrip() {
        User user = createVerifiedUser("emptytrip");
        LocalDate today = LocalDate.now();
        Trip trip = tripService.createForItinerary(user.getId(), "JP",
                today.minusDays(5), today.minusDays(1), "即将变空");
        Itinerary itinerary = insertItinerary(user, trip.getId());
        long noteId = insertNote(user, trip.getId(), "JP", today.minusDays(2));

        boolean deletedAfterItinerary = itineraryService.delete(user.getId(), itinerary.getId()).tripDeleted();
        assertThat(deletedAfterItinerary).isFalse();
        assertThat(tripService.detail(user.getId(), trip.getId()).effectiveStatus()).isNotNull();

        boolean deletedAfterNote = noteService.delete(user.getId(), noteId).tripDeleted();
        assertThat(deletedAfterNote).isTrue();
        assertThatThrownBy(() -> tripService.detail(user.getId(), trip.getId()))
                .isInstanceOf(BusinessException.class);
    }

    private void assertEffective(User user, Long tripId, String expected) {
        assertThat(tripService.detail(user.getId(), tripId).effectiveStatus()).isEqualTo(expected);
    }

    private Trip insertDatelessTrip(User user, String countryCode, String name) {
        Trip trip = new Trip();
        trip.setUserId(user.getId());
        trip.setCountryCode(countryCode);
        trip.setName(name);
        trip.setStatus(TripStatus.PLANNED);
        trip.setStatusManual(false);
        tripMapper.insert(trip);
        return trip;
    }

    private Itinerary insertItinerary(User user, Long tripId) {
        Itinerary itinerary = new Itinerary();
        itinerary.setUserId(user.getId());
        itinerary.setTripId(tripId);
        itinerary.setCountryCode("JP");
        itinerary.setTitle("状态行程");
        itinerary.setStartDate(LocalDate.now().minusDays(5));
        itinerary.setEndDate(LocalDate.now().minusDays(3));
        itinerary.setStatus(ItineraryStatus.SAVED);
        itineraryMapper.insert(itinerary);
        return itinerary;
    }

    private long insertNote(User user, Long tripId, String countryCode, LocalDate date) {
        jdbcTemplate.update("INSERT INTO travel_note "
                        + "(user_id, trip_id, country_code, city_code, title, content, created_at) "
                        + "VALUES (?, ?, ?, 'TYO', '状态游记', 'x', ?)",
                user.getId(), tripId, countryCode,
                Timestamp.valueOf(date.atTime(10, 0)));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM travel_note WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, user.getId());
    }

    private void insertExpense(User user, Long tripId, String countryCode, LocalDate date) {
        jdbcTemplate.update("INSERT INTO expense "
                        + "(user_id, trip_id, country_code, city_code, currency, amount, "
                        + "exchange_rate_snapshot, category, expense_date) "
                        + "VALUES (?, ?, ?, 'TYO', 'JPY', 100.00, 0.050000, 'FOOD', ?)",
                user.getId(), tripId, countryCode, date);
    }
}