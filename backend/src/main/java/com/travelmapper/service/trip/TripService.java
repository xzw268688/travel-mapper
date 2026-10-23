package com.travelmapper.service.trip;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.controller.trip.dto.TripDeleteResponse;
import com.travelmapper.controller.trip.dto.TripRenameResponse;
import com.travelmapper.controller.trip.dto.TripView;
import com.travelmapper.repository.entity.Itinerary;
import com.travelmapper.repository.entity.Trip;
import com.travelmapper.repository.mapper.ItineraryMapper;
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

    private final TripMapper tripMapper;
    private final ItineraryMapper itineraryMapper;
    private final CatalogService catalogService;
    private final JdbcTemplate jdbcTemplate;

    public TripService(TripMapper tripMapper, ItineraryMapper itineraryMapper,
                       CatalogService catalogService, JdbcTemplate jdbcTemplate) {
        this.tripMapper = tripMapper;
        this.itineraryMapper = itineraryMapper;
        this.catalogService = catalogService;
        this.jdbcTemplate = jdbcTemplate;
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
