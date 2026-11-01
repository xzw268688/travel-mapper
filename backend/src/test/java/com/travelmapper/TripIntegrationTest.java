package com.travelmapper;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.controller.trip.dto.TripDeleteResponse;
import com.travelmapper.repository.entity.Itinerary;
import com.travelmapper.repository.entity.Trip;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.ItineraryStatus;
import com.travelmapper.repository.mapper.ItineraryMapper;
import com.travelmapper.repository.mapper.TripMapper;
import com.travelmapper.service.trip.TripService;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripIntegrationTest extends AuthTestSupport {

    @Autowired
    private TripService tripService;

    @Autowired
    private ItineraryMapper itineraryMapper;

    @Autowired
    private TripMapper tripMapper;

    @Test
    void createAttachRenameAndRecalculateDates() {
        User user = createVerifiedUser("trip");
        Trip trip = tripService.createForItinerary(user.getId(), "JP",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 5), null);
        assertThat(trip.getName()).isEqualTo("日本 · 2025-06");
        assertThat(trip.getStartDate()).isEqualTo(LocalDate.of(2025, 6, 1));

        Itinerary later = insertItinerary(user, trip.getId(), "JP", "later",
                LocalDate.of(2025, 6, 3), LocalDate.of(2025, 6, 5));
        Itinerary earlier = insertItinerary(user, trip.getId(), "JP", "earlier",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 2));
        tripService.recalculateDates(trip.getId());
        Trip recalculated = tripMapper.selectById(trip.getId());
        assertThat(recalculated.getStartDate()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(recalculated.getEndDate()).isEqualTo(LocalDate.of(2025, 6, 5));

        itineraryMapper.deleteById(earlier.getId());
        tripService.recalculateDates(trip.getId());
        Trip afterDelete = tripMapper.selectById(trip.getId());
        assertThat(afterDelete.getStartDate()).isEqualTo(LocalDate.of(2025, 6, 3));

        itineraryMapper.deleteById(later.getId());
        tripService.recalculateDates(trip.getId());
        Trip empty = tripMapper.selectById(trip.getId());
        assertThat(empty.getStartDate()).isNull();
        assertThat(empty.getEndDate()).isNull();

        assertThat(tripService.rename(user.getId(), trip.getId(), "日本关西行").name())
                .isEqualTo("日本关西行");
        assertThatThrownBy(() -> tripService.rename(user.getId(), trip.getId(), " "))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
        assertThatThrownBy(() -> tripService.rename(user.getId(), trip.getId(), "a".repeat(31)))
                .isInstanceOf(BusinessException.class);

        Trip custom = tripService.createForItinerary(user.getId(), "JP",
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 3), "自定义旅行");
        assertThat(custom.getName()).isEqualTo("自定义旅行");

        assertThatThrownBy(() -> tripService.attachOrCreate(user.getId(), trip.getId(), "TH",
                null, LocalDate.now(), LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);

        User other = createVerifiedUser("tripo");
        assertThatThrownBy(() -> tripService.attachOrCreate(other.getId(), trip.getId(), "JP",
                null, LocalDate.now(), LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TRIP_NOT_FOUND);

        Trip created = tripService.attachOrCreate(user.getId(), null, "JP", null,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 2));
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("日本 · 2025-09");
        assertThat(tripService.attachOrCreate(user.getId(), trip.getId(), "JP",
                null, LocalDate.now(), LocalDate.now()).getId()).isEqualTo(trip.getId());
    }

    @Test
    void deleteTripDetachesDataAndControllerHonoursForce() throws Exception {
        User user = createVerifiedUser("tripdel");
        Trip trip = tripService.createForItinerary(user.getId(), "JP",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 5), "待删除旅行");
        Itinerary itinerary = insertItinerary(user, trip.getId(), "JP", "被挂载行程",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 2));
        jdbcTemplate.update("INSERT INTO travel_note (user_id, country_code, city_code, trip_id, title, "
                        + "content, content_text, visibility, takedown, deleted) "
                        + "VALUES (?, 'JP', 'TYO', ?, '被挂载游记', '<p>x</p>', 'x', 'PRIVATE', 0, 0)",
                user.getId(), trip.getId());
        jdbcTemplate.update("INSERT INTO expense (user_id, country_code, city_code, trip_id, currency, "
                        + "amount, category, expense_date, deleted) VALUES (?, 'JP', 'TYO', ?, 'JPY', 100, "
                        + "'FOOD', CURDATE(), 0)", user.getId(), trip.getId());

        assertThatThrownBy(() -> tripService.delete(user.getId(), trip.getId(), false))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TRIP_HAS_CONTENT);

        TripDeleteResponse response = tripService.delete(user.getId(), trip.getId(), true);
        assertThat(response.detached().itineraries()).isEqualTo(1);
        assertThat(response.detached().notes()).isEqualTo(1);
        assertThat(response.detached().expenses()).isEqualTo(1);
        assertThat(tripMapper.selectById(trip.getId())).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT trip_id FROM itinerary WHERE id = ?", Long.class, itinerary.getId())).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM travel_note WHERE trip_id = ?", Integer.class, trip.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expense WHERE trip_id = ?", Integer.class, trip.getId())).isZero();

        Trip second = tripService.createForItinerary(user.getId(), "JP",
                LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 2), "接口删除旅行");
        insertItinerary(user, second.getId(), "JP", "接口行程",
                LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 2));
        mockMvc.perform(withToken(delete("/api/trips/" + second.getId()), user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2017));
        mockMvc.perform(withToken(delete("/api/trips/" + second.getId()).param("force", "true"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detached.itineraries").value(1));
    }

    @Test
    void listReturnsCountsSubtotalsAndOnlyOwnTrips() throws Exception {
        User user = createVerifiedUser("triplist");
        Trip first = tripService.createForItinerary(user.getId(), "JP",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 5), "第一次日本");
        Trip second = tripService.createForItinerary(user.getId(), "JP",
                LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 5), "第二次日本");
        insertItinerary(user, first.getId(), "JP", "行程一", LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 2));
        jdbcTemplate.update("INSERT INTO travel_note (user_id, country_code, city_code, trip_id, title, "
                        + "content, content_text, visibility, takedown, deleted) "
                        + "VALUES (?, 'JP', 'TYO', ?, '游记一', '<p>x</p>', 'x', 'PRIVATE', 0, 0)",
                user.getId(), first.getId());
        jdbcTemplate.update("INSERT INTO expense (user_id, country_code, city_code, trip_id, currency, amount, "
                        + "exchange_rate_snapshot, category, expense_date, deleted) "
                        + "VALUES (?, 'JP', 'TYO', ?, 'JPY', 100, 0.500000, 'FOOD', CURDATE(), 0)",
                user.getId(), first.getId());

        mockMvc.perform(withToken(get("/api/trips").param("countryCode", "JP"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data[1].itineraryCount").value(1))
                .andExpect(jsonPath("$.data[1].noteCount").value(1))
                .andExpect(jsonPath("$.data[1].expenseCount").value(1))
                .andExpect(jsonPath("$.data[1].expenseCny").value("50.00"));

        User other = createVerifiedUser("triplist2");
        tripService.createForItinerary(other.getId(), "JP",
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 2), "别人的旅行");
        mockMvc.perform(withToken(get("/api/trips").param("countryCode", "JP"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void resolveTripIdPrefersItineraryThenRecentTrip() {
        User user = createVerifiedUser("tripresolve");
        Trip older = tripService.createForItinerary(user.getId(), "JP",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 5), "旧旅行");
        Trip newer = tripService.createForItinerary(user.getId(), "JP",
                LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 5), "新旅行");
        Itinerary linked = insertItinerary(user, older.getId(), "JP", "关联行程",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 2));
        assertThat(tripService.resolveTripId(user.getId(), "JP", linked.getId(), null))
                .isEqualTo(older.getId());
        assertThat(tripService.resolveTripId(user.getId(), "JP", null, null))
                .isEqualTo(newer.getId());
        assertThat(tripService.resolveTripId(user.getId(), "JP", null, older.getId()))
                .isEqualTo(older.getId());

        Itinerary ungrouped = insertItinerary(user, null, "JP", "未归组行程",
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 2));
        assertThat(tripService.resolveTripId(user.getId(), "JP", ungrouped.getId(), null)).isNull();

        assertThatThrownBy(() -> tripService.resolveTripId(user.getId(), "TH", null, older.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);

        User other = createVerifiedUser("tripresolve2");
        assertThatThrownBy(() -> tripService.resolveTripId(other.getId(), "JP", null, older.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    void tripDetailAggregatesChildrenAndCapsRecentExpenses() throws Exception {
        User user = createVerifiedUser("triphub");
        LocalDate start = LocalDate.now().plusDays(1);
        Trip trip = tripService.createForItinerary(user.getId(), "JP",
                start, start.plusDays(2), "东京三日");
        insertItinerary(user, trip.getId(), "JP", "第一天", start, start.plusDays(2));
        insertItinerary(user, trip.getId(), "JP", "第二天", start, start.plusDays(1));

        jdbcTemplate.update("INSERT INTO travel_note (user_id, trip_id, country_code, city_code, "
                        + "title, content, content_text, visibility, takedown, deleted) "
                        + "VALUES (?, ?, 'JP', 'TYO', '箱根一日', '<p>x</p>', 'x', 'PRIVATE', 0, 0)",
                user.getId(), trip.getId());

        for (int i = 0; i < 30; i++) {
            boolean unconfigured = i % 6 == 0;
            jdbcTemplate.update("INSERT INTO expense (user_id, trip_id, country_code, city_code, "
                            + "currency, amount, exchange_rate_snapshot, category, expense_date, remark) "
                            + "VALUES (?, ?, 'JP', 'TYO', ?, 100.00, ?, 'FOOD', ?, ?)",
                    user.getId(), trip.getId(), unconfigured ? "XYZ" : "JPY",
                    unconfigured ? null : new java.math.BigDecimal("0.048000"),
                    start, "perf-" + i);
        }

        mockMvc.perform(withToken(get("/api/trips/" + trip.getId()), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("东京三日"))
                .andExpect(jsonPath("$.data.countryName").value("日本"))
                .andExpect(jsonPath("$.data.summary.itineraryCount").value(2))
                .andExpect(jsonPath("$.data.summary.noteCount").value(1))
                .andExpect(jsonPath("$.data.summary.expenseCount").value(30))
                .andExpect(jsonPath("$.data.summary.hasUnconfiguredCurrency").value(true))
                .andExpect(jsonPath("$.data.summary.unconfiguredCurrencies[0]").value("XYZ"))
                .andExpect(jsonPath("$.data.itineraries.length()").value(2))
                .andExpect(jsonPath("$.data.notes.length()").value(1))
                .andExpect(jsonPath("$.data.recentExpenses.length()").value(10));

        User other = createVerifiedUser("triphub2");
        mockMvc.perform(withToken(get("/api/trips/" + trip.getId()), other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2015));
        mockMvc.perform(withToken(get("/api/trips/99999999"), user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2015));
    }

    private Itinerary insertItinerary(User user, Long tripId, String countryCode, String title,
                                      LocalDate start, LocalDate end) {
        Itinerary itinerary = new Itinerary();
        itinerary.setUserId(user.getId());
        itinerary.setTripId(tripId);
        itinerary.setCountryCode(countryCode);
        itinerary.setTitle(title + "-" + UUID.randomUUID());
        itinerary.setStartDate(start);
        itinerary.setEndDate(end);
        itinerary.setStatus(ItineraryStatus.SAVED);
        itineraryMapper.insert(itinerary);
        return itinerary;
    }
}
