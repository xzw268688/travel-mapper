package com.travelmapper.service.trip;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.controller.trip.dto.TripDeleteResponse;
import com.travelmapper.controller.trip.dto.TripDetailView;
import com.travelmapper.controller.trip.dto.TripRenameResponse;
import com.travelmapper.controller.trip.dto.TripStatusResponse;
import com.travelmapper.controller.trip.dto.TripTimelineView;
import com.travelmapper.controller.trip.dto.TripView;
import com.travelmapper.repository.entity.Expense;
import com.travelmapper.repository.entity.Itinerary;
import com.travelmapper.repository.entity.TravelNote;
import com.travelmapper.repository.entity.Trip;
import com.travelmapper.repository.mapper.ExpenseMapper;
import com.travelmapper.repository.mapper.ItineraryMapper;
import com.travelmapper.repository.mapper.TravelNoteMapper;
import com.travelmapper.repository.enums.EffectiveTripStatus;
import com.travelmapper.repository.enums.TripStatus;
import com.travelmapper.repository.mapper.TripMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.travelmapper.service.catalog.CatalogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TripService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int RECENT_EXPENSE_LIMIT = 10;
    /** 同国家两次行程相隔不超过该天数时视为同一次旅行。 */
    public static final int GROUPING_WINDOW_DAYS = 30;

    private final TripMapper tripMapper;
    private final ItineraryMapper itineraryMapper;
    private final TravelNoteMapper travelNoteMapper;
    private final ExpenseMapper expenseMapper;
    private final CatalogService catalogService;
    private final JdbcTemplate jdbcTemplate;
    private final TripStatusResolver statusResolver;
    private final AuditLogService auditLogService;

    public TripService(TripMapper tripMapper, ItineraryMapper itineraryMapper,
                       TravelNoteMapper travelNoteMapper, ExpenseMapper expenseMapper,
                       CatalogService catalogService, JdbcTemplate jdbcTemplate,
                       TripStatusResolver statusResolver, AuditLogService auditLogService) {
        this.tripMapper = tripMapper;
        this.itineraryMapper = itineraryMapper;
        this.travelNoteMapper = travelNoteMapper;
        this.expenseMapper = expenseMapper;
        this.catalogService = catalogService;
        this.jdbcTemplate = jdbcTemplate;
        this.statusResolver = statusResolver;
        this.auditLogService = auditLogService;
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
                userId, null, null, tripId, null, null, null);
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
        TripStatusResolver.Result status = statusResolver.resolve(snapshot(trip));
        return new TripDetailView(
                trip.getId(), trip.getName(), trip.getCountryCode(),
                catalogService.countryName(trip.getCountryCode()),
                trip.getStartDate(), trip.getEndDate(),
                status.effectiveStartDate(), status.effectiveEndDate(),
                trip.getStatus() == null ? "PLANNED" : trip.getStatus().name(),
                status.effectiveStatus().name(),
                Boolean.TRUE.equals(trip.getStatusManual()),
                status.expired(),
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
        trip.setStatus(TripStatus.PLANNED);
        trip.setStatusManual(false);
        tripMapper.insert(trip);
        return trip;
    }

    /**
     * 按“同国家 + 日期间隔 ≤30 天”匹配已有旅行；匹配不到才新建。
     * 多个匹配时取间隔最小的一次。
     */
    public Trip resolveForItinerary(Long userId, String countryCode, LocalDate start, LocalDate end) {
        List<Trip> trips = tripMapper.selectList(new LambdaQueryWrapper<Trip>()
                .eq(Trip::getUserId, userId)
                .eq(Trip::getCountryCode, countryCode));
        Trip best = null;
        long bestGap = Long.MAX_VALUE;
        for (Trip trip : trips) {
            if (trip.getStartDate() == null || trip.getEndDate() == null) {
                continue;
            }
            long gap;
            if (start.isAfter(trip.getEndDate())) {
                gap = ChronoUnit.DAYS.between(trip.getEndDate(), start);
            } else if (end.isBefore(trip.getStartDate())) {
                gap = ChronoUnit.DAYS.between(end, trip.getStartDate());
            } else {
                gap = 0;
            }
            if (gap <= GROUPING_WINDOW_DAYS && gap < bestGap) {
                best = trip;
                bestGap = gap;
            }
        }
        return best != null ? best
                : createForItinerary(userId, countryCode, start, end, null);
    }

    /**
     * 按日期匹配归档：记录日期与归档区间的前后距离 ≤ windowDays 天即命中。
     * 多个命中时取距离最小、结束日期最晚的一次。
     */
    public Trip resolveForDate(Long userId, String countryCode, LocalDate date, int windowDays) {
        List<Trip> trips = tripMapper.selectList(new LambdaQueryWrapper<Trip>()
                .eq(Trip::getUserId, userId)
                .eq(Trip::getCountryCode, countryCode));
        Trip best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Trip trip : trips) {
            if (trip.getStartDate() == null || trip.getEndDate() == null) {
                continue;
            }
            long distance;
            if (date.isBefore(trip.getStartDate())) {
                distance = ChronoUnit.DAYS.between(date, trip.getStartDate());
            } else if (date.isAfter(trip.getEndDate())) {
                distance = ChronoUnit.DAYS.between(trip.getEndDate(), date);
            } else {
                distance = 0;
            }
            if (distance > windowDays) {
                continue;
            }
            if (distance < bestDistance
                    || (distance == bestDistance && best != null
                    && trip.getEndDate().isAfter(best.getEndDate()))) {
                best = trip;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** 匹配不到归档时新建一个单日归档，保证游记/消费也能形成归档。 */
    public Trip resolveOrCreateForDate(Long userId, String countryCode, LocalDate date) {
        Trip matched = resolveForDate(userId, countryCode, date, GROUPING_WINDOW_DAYS);
        return matched != null ? matched
                : createForItinerary(userId, countryCode, date, date, null);
    }

    public Trip attachOrCreate(Long userId, Long tripId, String rawCountryCode, String tripName,
                               LocalDate startDate, LocalDate endDate) {
        String countryCode = rawCountryCode.toUpperCase(Locale.ROOT);
        if (tripId == null) {
            return resolveForItinerary(userId, countryCode, startDate, endDate);
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
        auditLogService.record(userId, "TRIP_UPDATE", "TRIP", tripId,
                Map.of("changed", List.of("name")), null);
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

        Long newTripId = null;
        String newTripName = null;
        if ((itineraries + notes + expenses) > 0) {
            newTripName = unnamedTripName(tripId);
            Trip newTrip = new Trip();
            newTrip.setUserId(userId);
            newTrip.setCountryCode(trip.getCountryCode());
            newTrip.setName(newTripName);
            newTrip.setStatus(TripStatus.PLANNED);
            newTrip.setStatusManual(false);
            tripMapper.insert(newTrip);
            newTripId = newTrip.getId();

            jdbcTemplate.update("UPDATE itinerary SET trip_id = ? WHERE trip_id = ? AND deleted = 0",
                    newTripId, tripId);
            jdbcTemplate.update("UPDATE travel_note SET trip_id = ? WHERE trip_id = ? AND deleted = 0",
                    newTripId, tripId);
            jdbcTemplate.update("UPDATE expense SET trip_id = ? WHERE trip_id = ? AND deleted = 0",
                    newTripId, tripId);
            recalculateDates(newTripId);
        }

        tripMapper.deleteById(trip.getId());
        auditLogService.record(userId, "TRIP_DELETE", "TRIP", tripId,
                Map.of("itineraries", itineraries, "notes", notes, "expenses", expenses,
                        "detached", newTripId != null), null);
        return new TripDeleteResponse(tripId,
                new TripDeleteResponse.Detached(itineraries, notes, expenses),
                newTripId, newTripName);
    }

    @Transactional
    public TripStatusResponse updateStatus(Long userId, Long tripId, String rawStatus) {
        Trip trip = requireOwnedTrip(userId, tripId);
        TripStatus status;
        try {
            status = TripStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "status 必须为 PLANNED 或 COMPLETED");
        }
        trip.setStatus(status);
        trip.setStatusManual(true);
        tripMapper.updateById(trip);
        auditLogService.record(userId, "TRIP_STATUS", "TRIP", tripId,
                Map.of("status", status.name()), null);
        return statusResponse(trip);
    }

    @Transactional
    public TripStatusResponse resetStatus(Long userId, Long tripId) {
        Trip trip = requireOwnedTrip(userId, tripId);
        trip.setStatusManual(false);
        tripMapper.updateById(trip);
        auditLogService.record(userId, "TRIP_STATUS", "TRIP", tripId,
                Map.of("status", "AUTO"), null);
        return statusResponse(trip);
    }

    /** 删除内容后调用：旅行已无任何未删除内容时自动软删除。 */
    @Transactional
    public boolean deleteIfEmpty(Long tripId) {
        if (tripId == null) {
            return false;
        }
        Map<String, Object> counts = jdbcTemplate.queryForMap(
                "SELECT "
                        + "(SELECT COUNT(*) FROM itinerary WHERE trip_id = ? AND deleted = 0) AS itineraries, "
                        + "(SELECT COUNT(*) FROM travel_note WHERE trip_id = ? AND deleted = 0) AS notes, "
                        + "(SELECT COUNT(*) FROM expense WHERE trip_id = ? AND deleted = 0) AS expenses",
                tripId, tripId, tripId);
        int total = ((Number) counts.get("itineraries")).intValue()
                + ((Number) counts.get("notes")).intValue()
                + ((Number) counts.get("expenses")).intValue();
        if (total > 0) {
            return false;
        }
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null) {
            return false;
        }
        tripMapper.deleteById(tripId);
        return true;
    }

    /**
     * 时间线：每个旅行（归档）一个节点，按有效起始日期正序；无日期时按创建日期排序。
     */
    public List<TripTimelineView> timeline(Long userId) {
        return jdbcTemplate.query(
                "SELECT t.id, t.country_code, t.name, t.start_date, t.end_date, t.status, "
                        + "t.status_manual, t.created_at, "
                        + "(SELECT COUNT(*) FROM itinerary i WHERE i.trip_id = t.id AND i.deleted = 0) AS itinerary_count, "
                        + "(SELECT COUNT(*) FROM travel_note n WHERE n.trip_id = t.id AND n.deleted = 0) AS note_count, "
                        + "(SELECT COUNT(*) FROM expense e WHERE e.trip_id = t.id AND e.deleted = 0) AS expense_count, "
                        + "(SELECT COALESCE(SUM(e.amount_cny), 0) FROM expense e "
                        + " WHERE e.trip_id = t.id AND e.deleted = 0) AS expense_cny, "
                        + "(SELECT MIN(DATE(n.created_at)) FROM travel_note n "
                        + " WHERE n.trip_id = t.id AND n.deleted = 0) AS note_min, "
                        + "(SELECT MAX(DATE(n.created_at)) FROM travel_note n "
                        + " WHERE n.trip_id = t.id AND n.deleted = 0) AS note_max, "
                        + "(SELECT MIN(e.expense_date) FROM expense e "
                        + " WHERE e.trip_id = t.id AND e.deleted = 0) AS expense_min, "
                        + "(SELECT MAX(e.expense_date) FROM expense e "
                        + " WHERE e.trip_id = t.id AND e.deleted = 0) AS expense_max "
                        + "FROM trip t WHERE t.user_id = ? AND t.deleted = 0 "
                        + "ORDER BY COALESCE(t.start_date, DATE(t.created_at)) ASC, t.id ASC",
                (rs, rowNum) -> {
                    String countryCode = rs.getString("country_code");
                    LocalDate start = toLocalDate(rs.getDate("start_date"));
                    LocalDate end = toLocalDate(rs.getDate("end_date"));
                    LocalDate recordStart = earliest(
                            toLocalDate(rs.getDate("note_min")),
                            toLocalDate(rs.getDate("expense_min")));
                    LocalDate recordEnd = latest(
                            toLocalDate(rs.getDate("note_max")),
                            toLocalDate(rs.getDate("expense_max")));
                    boolean statusManual = rs.getInt("status_manual") == 1;
                    TripStatus stored = rs.getString("status") == null
                            ? null : TripStatus.valueOf(rs.getString("status"));
                    TripStatusResolver.Result resolved = statusResolver.resolve(
                            new TripStatusResolver.Snapshot(
                                    statusManual, stored, start, end,
                                    recordStart != null, recordStart, recordEnd));
                    return new TripTimelineView(
                            rs.getLong("id"),
                            rs.getString("name"),
                            countryCode,
                            catalogService.countryName(countryCode),
                            start,
                            end,
                            resolved.effectiveStatus().name(),
                            rs.getInt("itinerary_count"),
                            rs.getInt("note_count"),
                            rs.getInt("expense_count"),
                            money(rs.getBigDecimal("expense_cny")));
                },
                userId);
    }

    public List<TripView> list(Long userId, String rawCountryCode) {
        String countryCode = rawCountryCode.toUpperCase(Locale.ROOT);
        catalogService.requireEnabledCountry(countryCode);
        return jdbcTemplate.query(
                "SELECT t.id, t.country_code, t.name, t.start_date, t.end_date, t.status, "
                        + "t.status_manual, t.created_at, "
                        + "(SELECT COUNT(*) FROM itinerary i WHERE i.trip_id = t.id AND i.deleted = 0) AS itinerary_count, "
                        + "(SELECT COUNT(*) FROM travel_note n WHERE n.trip_id = t.id AND n.deleted = 0) AS note_count, "
                        + "(SELECT COUNT(*) FROM expense e WHERE e.trip_id = t.id AND e.deleted = 0) AS expense_count, "
                        + "(SELECT COALESCE(SUM(e.amount_cny), 0) FROM expense e "
                        + " WHERE e.trip_id = t.id AND e.deleted = 0) AS expense_cny, "
                        + "(SELECT MIN(DATE(n.created_at)) FROM travel_note n "
                        + " WHERE n.trip_id = t.id AND n.deleted = 0) AS note_min, "
                        + "(SELECT MAX(DATE(n.created_at)) FROM travel_note n "
                        + " WHERE n.trip_id = t.id AND n.deleted = 0) AS note_max, "
                        + "(SELECT MIN(e.expense_date) FROM expense e "
                        + " WHERE e.trip_id = t.id AND e.deleted = 0) AS expense_min, "
                        + "(SELECT MAX(e.expense_date) FROM expense e "
                        + " WHERE e.trip_id = t.id AND e.deleted = 0) AS expense_max "
                        + "FROM trip t WHERE t.user_id = ? AND t.country_code = ? AND t.deleted = 0 "
                        + "ORDER BY t.start_date DESC, t.created_at DESC",
                (rs, rowNum) -> {
                    LocalDate start = toLocalDate(rs.getDate("start_date"));
                    LocalDate end = toLocalDate(rs.getDate("end_date"));
                    LocalDate recordStart = earliest(
                            toLocalDate(rs.getDate("note_min")),
                            toLocalDate(rs.getDate("expense_min")));
                    LocalDate recordEnd = latest(
                            toLocalDate(rs.getDate("note_max")),
                            toLocalDate(rs.getDate("expense_max")));
                    boolean statusManual = rs.getInt("status_manual") == 1;
                    TripStatus stored = rs.getString("status") == null
                            ? null : TripStatus.valueOf(rs.getString("status"));
                    TripStatusResolver.Result resolved = statusResolver.resolve(
                            new TripStatusResolver.Snapshot(
                                    statusManual, stored, start, end,
                                    recordStart != null, recordStart, recordEnd));
                    return new TripView(
                            rs.getLong("id"),
                            rs.getString("country_code"),
                            rs.getString("name"),
                            start,
                            end,
                            resolved.effectiveStartDate(),
                            resolved.effectiveEndDate(),
                            rs.getInt("itinerary_count"),
                            rs.getInt("note_count"),
                            rs.getInt("expense_count"),
                            money(rs.getBigDecimal("expense_cny")),
                            rs.getString("status"),
                            resolved.effectiveStatus().name(),
                            statusManual,
                            resolved.expired(),
                            rs.getTimestamp("created_at").toLocalDateTime());
                },
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

    private TripStatusResponse statusResponse(Trip trip) {
        TripStatusResolver.Result result = statusResolver.resolve(snapshot(trip));
        return new TripStatusResponse(
                trip.getId(),
                trip.getName(),
                trip.getStatus() == null ? "PLANNED" : trip.getStatus().name(),
                result.effectiveStatus().name(),
                Boolean.TRUE.equals(trip.getStatusManual()),
                result.expired(),
                result.effectiveStartDate(),
                result.effectiveEndDate());
    }

    private TripStatusResolver.Snapshot snapshot(Trip trip) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT "
                        + "(SELECT COUNT(*) FROM travel_note WHERE trip_id = ? AND deleted = 0) AS note_count, "
                        + "(SELECT COUNT(*) FROM expense WHERE trip_id = ? AND deleted = 0) AS expense_count, "
                        + "(SELECT MIN(DATE(created_at)) FROM travel_note "
                        + " WHERE trip_id = ? AND deleted = 0) AS note_min, "
                        + "(SELECT MAX(DATE(created_at)) FROM travel_note "
                        + " WHERE trip_id = ? AND deleted = 0) AS note_max, "
                        + "(SELECT MIN(expense_date) FROM expense "
                        + " WHERE trip_id = ? AND deleted = 0) AS expense_min, "
                        + "(SELECT MAX(expense_date) FROM expense "
                        + " WHERE trip_id = ? AND deleted = 0) AS expense_max",
                trip.getId(), trip.getId(), trip.getId(),
                trip.getId(), trip.getId(), trip.getId());
        LocalDate noteMin = toLocalDate((Date) row.get("note_min"));
        LocalDate noteMax = toLocalDate((Date) row.get("note_max"));
        LocalDate expenseMin = toLocalDate((Date) row.get("expense_min"));
        LocalDate expenseMax = toLocalDate((Date) row.get("expense_max"));
        boolean hasRecords = ((Number) row.get("note_count")).intValue() > 0
                || ((Number) row.get("expense_count")).intValue() > 0;
        return new TripStatusResolver.Snapshot(
                Boolean.TRUE.equals(trip.getStatusManual()),
                trip.getStatus(),
                trip.getStartDate(),
                trip.getEndDate(),
                hasRecords,
                earliest(noteMin, expenseMin),
                latest(noteMax, expenseMax));
    }

    private String unnamedTripName(Long tripId) {
        Date earliest = jdbcTemplate.queryForObject(
                "SELECT MIN(d) FROM ("
                        + " SELECT start_date AS d FROM itinerary WHERE trip_id = ? AND deleted = 0"
                        + " UNION ALL SELECT DATE(created_at) AS d FROM travel_note WHERE trip_id = ? AND deleted = 0"
                        + " UNION ALL SELECT expense_date AS d FROM expense WHERE trip_id = ? AND deleted = 0"
                        + ") records",
                Date.class, tripId, tripId, tripId);
        LocalDate month = earliest == null ? LocalDate.now() : earliest.toLocalDate();
        return "未命名旅行 · " + MONTH_FORMAT.format(month);
    }

    private LocalDate earliest(LocalDate left, LocalDate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private LocalDate latest(LocalDate left, LocalDate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
