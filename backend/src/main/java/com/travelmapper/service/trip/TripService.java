package com.travelmapper.service.trip;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.controller.trip.dto.TripDeleteResponse;
import com.travelmapper.controller.trip.dto.TripDetailView;
import com.travelmapper.controller.trip.dto.TripRenameResponse;
import com.travelmapper.controller.trip.dto.TripView;
import com.travelmapper.repository.entity.Expense;
import com.travelmapper.repository.entity.Itinerary;
import com.travelmapper.repository.entity.TravelNote;
import com.travelmapper.repository.entity.Trip;
import com.travelmapper.repository.mapper.ExpenseMapper;
import com.travelmapper.repository.mapper.ItineraryMapper;
import com.travelmapper.repository.mapper.TravelNoteMapper;
import com.travelmapper.repository.mapper.TripMapper;
import com.travelmapper.service.catalog.CatalogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TripService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int RECENT_EXPENSE_LIMIT = 10;

    private final TripMapper tripMapper;
    private final ItineraryMapper itineraryMapper;
    private final TravelNoteMapper travelNoteMapper;
    private final ExpenseMapper expenseMapper;
    private final CatalogService catalogService;
    private final JdbcTemplate jdbcTemplate;

    public TripService(TripMapper tripMapper, ItineraryMapper itineraryMapper,
                       TravelNoteMapper travelNoteMapper, ExpenseMapper expenseMapper,
                       CatalogService catalogService, JdbcTemplate jdbcTemplate) {
        this.tripMapper = tripMapper;
        this.itineraryMapper = itineraryMapper;
        this.travelNoteMapper = travelNoteMapper;
        this.expenseMapper = expenseMapper;
        this.catalogService = catalogService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 旅行详情：概览 + 行程/游记全量 + 最近 10 条消费。 */
    public TripDetailView detail(Long userId, Long tripId) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || !userId.equals(trip.getUserId())) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        List<Itinerary> itineraryList = itineraryMapper.selectList(
                new LambdaQueryWrapper<Itinerary>()
                        .eq(Itinerary::getTripId, tripId)
                        .orderByDesc(Itinerary::getStartDate)
                        .orderByDesc(Itinerary::getId));
        List<TravelNote> noteList = travelNoteMapper.selectList(
                new LambdaQueryWrapper<TravelNote>()
                        .eq(TravelNote::getTripId, tripId)
                        .orderByDesc(TravelNote::getCreatedAt)
                        .orderByDesc(TravelNote::getId));
        ExpenseMapper.TripSummary totals = expenseMapper.selectTripSummary(userId, tripId);
        List<String> unconfigured = expenseMapper.selectUnconfiguredCurrencies(
                userId, null, tripId, null, null);
        List<Expense> recent = expenseMapper.selectList(
                new LambdaQueryWrapper<Expense>()
                        .eq(Expense::getUserId, userId)
                        .eq(Expense::getTripId, tripId)
                        .orderByDesc(Expense::getExpenseDate)
                        .orderByDesc(Expense::getId)
                        .last("LIMIT " + RECENT_EXPENSE_LIMIT));

        LocalDate today = LocalDate.now();
        List<TripDetailView.ItineraryItem> itineraries = itineraryList.stream()
                .map(item -> new TripDetailView.ItineraryItem(
                        item.getId(), item.getTitle(), item.getStartDate(), item.getEndDate(),
                        item.getStatus() == null ? "SAVED" : item.getStatus().name(),
                        item.getEndDate() != null && item.getEndDate().isBefore(today)))
                .toList();
        List<TripDetailView.NoteItem> notes = noteList.stream()
                .map(note -> new TripDetailView.NoteItem(
                        note.getId(), note.getTitle(),
                        note.getVisibility() == null ? "PRIVATE" : note.getVisibility().name(),
                        note.getCityCode(),
                        catalogService.cityName(note.getCountryCode(), note.getCityCode()),
                        Boolean.TRUE.equals(note.getTakedown()),
                        note.getCreatedAt()))
                .toList();
        List<TripDetailView.ExpenseItem> expenses = recent.stream()
                .map(item -> new TripDetailView.ExpenseItem(
                        item.getId(), item.getExpenseDate(),
                        item.getCategory() == null ? null : item.getCategory().name(),
                        money(item.getAmount()),
                        item.getCurrency(),
                        item.getAmountCny() == null ? null : money(item.getAmountCny()),
                        item.getExchangeRateSnapshot() == null,
                        item.getRemark()))
                .toList();
        int expenseCount = totals == null || totals.getExpenseCount() == null
                ? 0 : totals.getExpenseCount().intValue();
        BigDecimal totalCny = totals == null || totals.getTotalCny() == null
                ? BigDecimal.ZERO : totals.getTotalCny();
        return new TripDetailView(
                trip.getId(), trip.getName(), trip.getCountryCode(),
                catalogService.countryName(trip.getCountryCode()),
                trip.getStartDate(), trip.getEndDate(),
                new TripDetailView.Summary(
                        itineraryList.size(), noteList.size(), expenseCount, money(totalCny),
                        !unconfigured.isEmpty(), unconfigured),
                itineraries, notes, expenses);
    }

    @Transactional
    public Trip createForItinerary(Long userId, String rawCountryCode, LocalDate startDate,
                                   LocalDate endDate, String tripName) {
        String countryCode = rawCountryCode.toUpperCase(Locale.ROOT);
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "行程起止日期必填");
        }
        String name = tripName == null || tripName.isBlank()
                ? catalogService.countryName(countryCode) + " · " + startDate.format(MONTH_FORMAT)
                : tripName.trim();
        validateName(name);

        Trip trip = new Trip();
        trip.setUserId(userId);
        trip.setCountryCode(countryCode);
        trip.setName(name);
        trip.setStartDate(startDate);
        trip.setEndDate(endDate);
        tripMapper.insert(trip);
        return trip;
    }

    public Trip attachOrCreate(Long userId, Long tripId, String rawCountryCode, String tripName,
                               LocalDate startDate, LocalDate endDate) {
        String countryCode = rawCountryCode.toUpperCase(Locale.ROOT);
        if (tripId == null) {
            return createForItinerary(userId, countryCode, startDate, endDate, tripName);
        }
        Trip trip = requireOwnedTrip(userId, tripId);
        if (!trip.getCountryCode().equals(countryCode)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "行程国家与旅行不一致");
        }
        return trip;
    }

    public void recalculateDates(Long tripId) {
        tripMapper.recalculateDates(tripId);
    }

    @Transactional
    public TripRenameResponse rename(Long userId, Long tripId, String name) {
        Trip trip = requireOwnedTrip(userId, tripId);
        String trimmed = name == null ? "" : name.trim();
        validateName(trimmed);
        trip.setName(trimmed);
        tripMapper.updateById(trip);
        return new TripRenameResponse(trip.getId(), trip.getName());
    }

    @Transactional
    public TripDeleteResponse delete(Long userId, Long tripId, boolean force) {
        Trip trip = requireOwnedTrip(userId, tripId);
        Map<String, Object> counts = jdbcTemplate.queryForMap(
                "SELECT "
                        + "(SELECT COUNT(*) FROM itinerary WHERE trip_id = ? AND deleted = 0) AS itineraries, "
                        + "(SELECT COUNT(*) FROM travel_note WHERE trip_id = ? AND deleted = 0) AS notes, "
                        + "(SELECT COUNT(*) FROM expense WHERE trip_id = ? AND deleted = 0) AS expenses",
                tripId, tripId, tripId);
        int itineraries = ((Number) counts.get("itineraries")).intValue();
        int notes = ((Number) counts.get("notes")).intValue();
        int expenses = ((Number) counts.get("expenses")).intValue();
        if ((itineraries + notes + expenses) > 0 && !force) {
            throw new BusinessException(ErrorCode.TRIP_HAS_CONTENT);
        }

        jdbcTemplate.update("UPDATE itinerary SET trip_id = NULL WHERE trip_id = ? AND deleted = 0", tripId);
        jdbcTemplate.update("UPDATE travel_note SET trip_id = NULL WHERE trip_id = ? AND deleted = 0", tripId);
        jdbcTemplate.update("UPDATE expense SET trip_id = NULL WHERE trip_id = ? AND deleted = 0", tripId);
        tripMapper.deleteById(trip.getId());
        return new TripDeleteResponse(tripId,
                new TripDeleteResponse.Detached(itineraries, notes, expenses));
    }

    public List<TripView> list(Long userId, String rawCountryCode) {
        String countryCode = rawCountryCode.toUpperCase(Locale.ROOT);
        catalogService.requireEnabledCountry(countryCode);
        return jdbcTemplate.query(
                "SELECT t.id, t.country_code, t.name, t.start_date, t.end_date, t.created_at, "
                        + "(SELECT COUNT(*) FROM itinerary i WHERE i.trip_id = t.id AND i.deleted = 0) AS itinerary_count, "
                        + "(SELECT COUNT(*) FROM travel_note n WHERE n.trip_id = t.id AND n.deleted = 0) AS note_count, "
                        + "(SELECT COUNT(*) FROM expense e WHERE e.trip_id = t.id AND e.deleted = 0) AS expense_count, "
                        + "(SELECT COALESCE(SUM(e.amount_cny), 0) FROM expense e "
                        + " WHERE e.trip_id = t.id AND e.deleted = 0) AS expense_cny "
                        + "FROM trip t WHERE t.user_id = ? AND t.country_code = ? AND t.deleted = 0 "
                        + "ORDER BY t.start_date DESC, t.created_at DESC",
                (rs, rowNum) -> new TripView(
                        rs.getLong("id"),
                        rs.getString("country_code"),
                        rs.getString("name"),
                        toLocalDate(rs.getDate("start_date")),
                        toLocalDate(rs.getDate("end_date")),
                        rs.getInt("itinerary_count"),
                        rs.getInt("note_count"),
                        rs.getInt("expense_count"),
                        money(rs.getBigDecimal("expense_cny")),
                        rs.getTimestamp("created_at").toLocalDateTime()),
                userId, countryCode);
    }

    /**
     * Resolves the trip for a note/expense. A linked itinerary wins; otherwise an
     * explicit trip is validated, and the most recent trip in the country is the default.
     */
    public Long resolveTripId(Long userId, String rawCountryCode, Long itineraryId, Long requestedTripId) {
        String countryCode = rawCountryCode.toUpperCase(Locale.ROOT);
        if (itineraryId != null) {
            Itinerary itinerary = itineraryMapper.selectById(itineraryId);
            if (itinerary == null || !userId.equals(itinerary.getUserId())) {
                throw new BusinessException(ErrorCode.ITINERARY_NOT_FOUND);
            }
            if (!itinerary.getCountryCode().equals(countryCode)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "行程国家与数据不一致");
            }
            return itinerary.getTripId();
        }
        if (requestedTripId != null) {
            Trip trip = requireOwnedTrip(userId, requestedTripId);
            if (!trip.getCountryCode().equals(countryCode)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "旅行国家与数据不一致");
            }
            return requestedTripId;
        }
        List<Long> matches = jdbcTemplate.queryForList(
                "SELECT id FROM trip WHERE user_id = ? AND country_code = ? AND deleted = 0 "
                        + "ORDER BY (start_date IS NULL), start_date DESC, created_at DESC LIMIT 1",
                Long.class, userId, countryCode);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public Trip requireOwnedTrip(Long userId, Long tripId) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || !userId.equals(trip.getUserId())) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 30) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "旅行名称必须为 1-30 字");
        }
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
