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

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        assertThat(response.newTripId()).isNotNull();
        assertThat(response.newTripName()).startsWith("未命名旅行 · ");
        assertThat(tripMapper.selectById(trip.getId())).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT trip_id FROM itinerary WHERE id = ?", Long.class, itinerary.getId()))
                .isEqualTo(response.newTripId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM travel_note WHERE trip_id = ?", Integer.class, trip.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expense WHERE trip_id = ?", Integer.class, trip.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM travel_note WHERE trip_id = ?", Integer.class, response.newTripId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expense WHERE trip_id = ?", Integer.class, response.newTripId()))
                .isEqualTo(1);

        Trip second = tripService.createForItinerary(user.getId(), "JP",
                LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 2), "接口删除旅行");
        insertItinerary(user, second.getId(), "JP", "接口行程",
                LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 2));
        mockMvc.perform(withToken(delete("/api/trips/" + second.getId()), user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2017));
        mockMvc.perform(withToken(delete("/api/trips/" + second.getId()).param("force", "true"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detached.itineraries").value(1))
                .andExpect(jsonPath("$.data.newTripId").isNumber())
                .andExpect(jsonPath("$.data.newTripName").value(org.hamcrest.Matchers.startsWith("未命名旅行 · ")));
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

    @Test
    void itineraryAutoGroupsWithinThirtyDays() {
        User user = createVerifiedUser("autogroup");
        LocalDate start = LocalDate.now().plusDays(10);
        Trip first = tripService.attachOrCreate(user.getId(), null, "JP", null, start, start.plusDays(2));
        Trip close = tripService.attachOrCreate(user.getId(), null, "JP", null,
                start.plusDays(29), start.plusDays(31));
        assertThat(close.getId()).isEqualTo(first.getId());

        Trip far = tripService.attachOrCreate(user.getId(), null, "JP", null,
                start.plusDays(70), start.plusDays(72));
        assertThat(far.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void expenseAndNoteAutoAssignByDate() throws Exception {
        User user = createVerifiedUser("autodate");
        LocalDate today = LocalDate.now();
        Trip trip = tripService.attachOrCreate(user.getId(), null, "JP", null,
                today.minusDays(2), today.plusDays(2));

        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countryCode\":\"JP\",\"currency\":\"JPY\",\"amount\":\"100.00\","
                                + "\"category\":\"FOOD\",\"expenseDate\":\"" + today + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripInherited").value(false));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT trip_id FROM expense WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, user.getId())).isEqualTo(trip.getId());

        mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"自动归组\",\"content\":\"<p>x</p>\",\"images\":[],"
                                + "\"countryCode\":\"JP\",\"cityCode\":\"TYO\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT trip_id FROM travel_note WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, user.getId())).isEqualTo(trip.getId());
    }

    @Test
    void reclusterMergesCloseTripsAndRemovesEmptyOnes() throws Exception {
        User user = createVerifiedUser("recluster");
        LocalDate start = LocalDate.now().plusDays(10);
        Trip first = tripService.createForItinerary(user.getId(), "JP", start, start.plusDays(2), "A");
        Trip second = tripService.createForItinerary(user.getId(), "JP",
                start.plusDays(20), start.plusDays(22), "B");
        insertItinerary(user, first.getId(), "JP", "A1", start, start.plusDays(2));
        insertItinerary(user, second.getId(), "JP", "B1", start.plusDays(20), start.plusDays(22));

        mockMvc.perform(asAdmin(post("/api/admin/trips/recluster")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itineraries").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.mergedTrips").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        Long distinct = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT trip_id) FROM itinerary WHERE user_id = ? AND deleted = 0",
                Long.class, user.getId());
        assertThat(distinct).isEqualTo(1);

        User normal = createVerifiedUser("reclusternormal");
        mockMvc.perform(withToken(post("/api/admin/trips/recluster"), normal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    void expenseThenLaterItineraryShareOneArchive() throws Exception {
        User user = createVerifiedUser("backfill");
        LocalDate day = LocalDate.now().minusDays(2);

        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countryCode\":\"JP\",\"currency\":\"JPY\",\"amount\":\"100.00\","
                                + "\"category\":\"FOOD\",\"expenseDate\":\"" + day + "\"}"))
                .andExpect(status().isOk());
        Long expenseTrip = jdbcTemplate.queryForObject(
                "SELECT trip_id FROM expense WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, user.getId());
        assertThat(expenseTrip).isNotNull();

        // 10 天后才保存行程：应归入同一个归档（向前窗口 30 天）
        Trip later = tripService.attachOrCreate(user.getId(), null, "JP", null,
                day.plusDays(10), day.plusDays(12));
        assertThat(later.getId()).isEqualTo(expenseTrip);
    }

    @Test
    void statusToggleAndTimelineOrder() throws Exception {
        User user = createVerifiedUser("status");
        LocalDate firstStart = LocalDate.now().plusDays(5);
        Trip first = tripService.createForItinerary(user.getId(), "JP",
                firstStart, firstStart.plusDays(1), "第一段");
        Trip second = tripService.createForItinerary(user.getId(), "TH",
                firstStart.plusDays(40), firstStart.plusDays(42), "第二段");

        mockMvc.perform(withToken(get("/api/trips/" + first.getId()), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLANNED"));

        mockMvc.perform(withToken(put("/api/trips/" + first.getId() + "/status"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(get("/api/trips/" + first.getId()), user))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(withToken(put("/api/trips/" + first.getId() + "/status"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        User other = createVerifiedUser("status2");
        mockMvc.perform(withToken(put("/api/trips/" + first.getId() + "/status"), other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2015));

        mockMvc.perform(withToken(get("/api/trips/timeline"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(first.getId()))
                .andExpect(jsonPath("$.data[0].name").value("第一段"))
                .andExpect(jsonPath("$.data[0].countryName").value("日本"))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[1].id").value(second.getId()))
                .andExpect(jsonPath("$.data[1].name").value("第二段"))
                .andExpect(jsonPath("$.data[1].status").value("PLANNED"));
    }

    @Test
    void timelineListsTripsSortedByStartDateAndIsolatesUsers() throws Exception {
        User user = createVerifiedUser("timeline");

        // 同一旅行下的多条行程只占一个归档节点
        Trip shared = tripService.createForItinerary(user.getId(), "JP",
                LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 3), "关西行");
        insertItinerary(user, shared.getId(), "JP", "京都",
                LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 2));
        insertItinerary(user, shared.getId(), "JP", "大阪",
                LocalDate.of(2024, 5, 3), LocalDate.of(2024, 5, 3));

        // 只有游记/花费、没有行程的旅行照常出现
        Trip notesOnly = tripService.createForItinerary(user.getId(), "TH",
                LocalDate.of(2023, 12, 1), LocalDate.of(2023, 12, 5), "曼谷行");
        jdbcTemplate.update("UPDATE trip SET start_date = NULL, end_date = NULL WHERE id = ?",
                notesOnly.getId());
        jdbcTemplate.update("INSERT INTO travel_note "
                        + "(user_id, trip_id, country_code, city_code, title, content, created_at) "
                        + "VALUES (?, ?, 'TH', 'BKK', '曼谷游记', '正文', ?)",
                user.getId(), notesOnly.getId(), Timestamp.valueOf(LocalDateTime.of(2023, 12, 20, 10, 0)));
        jdbcTemplate.update("INSERT INTO expense "
                        + "(user_id, trip_id, country_code, city_code, currency, amount, "
                        + "exchange_rate_snapshot, category, expense_date) "
                        + "VALUES (?, ?, 'TH', 'BKK', 'THB', 100.00, 1.000000, 'FOOD', ?)",
                user.getId(), notesOnly.getId(), LocalDate.of(2023, 12, 10));

        // 未归组行程不属于任何归档，不产生节点
        Itinerary orphan = insertItinerary(user, null, "FR", "巴黎",
                LocalDate.of(2022, 9, 1), LocalDate.of(2022, 9, 3));

        // 行程已删除的旅行仍保留为归档节点
        Trip emptied = tripService.createForItinerary(user.getId(), "JP",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1), "已删行程");
        Itinerary deleted = insertItinerary(user, emptied.getId(), "JP", "已删除",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));
        itineraryMapper.deleteById(deleted.getId());

        // 他人数据不出现
        User other = createVerifiedUser("timeline-other");
        Trip otherTrip = tripService.createForItinerary(other.getId(), "JP",
                LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 1), "他人旅行");

        mockMvc.perform(withToken(get("/api/trips/timeline"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                // 2024 归档：两条行程合并为一个节点
                .andExpect(jsonPath("$.data[0].id").value(shared.getId()))
                .andExpect(jsonPath("$.data[0].name").value("关西行"))
                .andExpect(jsonPath("$.data[0].itineraryCount").value(2))
                .andExpect(jsonPath("$.data[0].countryName").value("日本"))
                .andExpect(jsonPath("$.data[0].status").value("PLANNED"))
                // 2025 归档：行程已删除，节点仍保留
                .andExpect(jsonPath("$.data[1].id").value(emptied.getId()))
                .andExpect(jsonPath("$.data[1].itineraryCount").value(0))
                // 无日期归档按创建时间排在最后
                .andExpect(jsonPath("$.data[2].id").value(notesOnly.getId()))
                .andExpect(jsonPath("$.data[2].noteCount").value(1))
                .andExpect(jsonPath("$.data[2].expenseCny").value("100.00"))
                .andExpect(jsonPath("$.data[2].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[?(@.id == " + otherTrip.getId() + ")]").isEmpty());

        assertThat(orphan.getId()).isNotNull();
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
