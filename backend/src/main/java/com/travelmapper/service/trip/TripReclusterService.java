package com.travelmapper.service.trip;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次性重算：按“同国家 + 30 天窗口”重新聚类行程，
 * 合并旧旅行并把游记/消费重新挂到新旅行，最后删除空旅行。
 */
@Service
public class TripReclusterService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TripMapper tripMapper;
    private final ItineraryMapper itineraryMapper;
    private final TravelNoteMapper noteMapper;
    private final ExpenseMapper expenseMapper;
    private final TripService tripService;
    private final CatalogService catalogService;
    private final JdbcTemplate jdbcTemplate;

    public TripReclusterService(TripMapper tripMapper, ItineraryMapper itineraryMapper,
                                TravelNoteMapper noteMapper, ExpenseMapper expenseMapper,
                                TripService tripService, CatalogService catalogService,
                                JdbcTemplate jdbcTemplate) {
        this.tripMapper = tripMapper;
        this.itineraryMapper = itineraryMapper;
        this.noteMapper = noteMapper;
        this.expenseMapper = expenseMapper;
        this.tripService = tripService;
        this.catalogService = catalogService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record Result(int itineraries, int mergedTrips, int deletedTrips) {
    }

    @Transactional
    public Result recluster() {
        List<Itinerary> itineraries = itineraryMapper.selectList(
                new LambdaQueryWrapper<Itinerary>()
                        .orderByAsc(Itinerary::getUserId)
                        .orderByAsc(Itinerary::getCountryCode)
                        .orderByAsc(Itinerary::getStartDate)
                        .orderByAsc(Itinerary::getId));

        Map<String, List<Itinerary>> groups = new LinkedHashMap<>();
        for (Itinerary itinerary : itineraries) {
            String key = itinerary.getUserId() + "|" + itinerary.getCountryCode();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(itinerary);
        }

        Map<Long, Long> remap = new HashMap<>();
        int merged = 0;
        for (List<Itinerary> group : groups.values()) {
            group.sort(Comparator.comparing(Itinerary::getStartDate)
                    .thenComparing(Itinerary::getId));
            List<List<Itinerary>> clusters = new ArrayList<>();
            List<Itinerary> current = null;
            LocalDate clusterEnd = null;
            for (Itinerary itinerary : group) {
                if (current == null
                        || itinerary.getStartDate().isAfter(clusterEnd.plusDays(
                        TripService.GROUPING_WINDOW_DAYS))) {
                    current = new ArrayList<>();
                    clusters.add(current);
                    clusterEnd = itinerary.getEndDate();
                } else if (itinerary.getEndDate().isAfter(clusterEnd)) {
                    clusterEnd = itinerary.getEndDate();
                }
                current.add(itinerary);
            }
            for (List<Itinerary> cluster : clusters) {
                merged += mergeCluster(cluster, remap);
            }
        }

        for (Map.Entry<Long, Long> entry : remap.entrySet()) {
            if (entry.getKey().equals(entry.getValue())) {
                continue;
            }
            jdbcTemplate.update("UPDATE travel_note SET trip_id = ? WHERE trip_id = ?",
                    entry.getValue(), entry.getKey());
            jdbcTemplate.update("UPDATE expense SET trip_id = ? WHERE trip_id = ?",
                    entry.getValue(), entry.getKey());
        }

        assignOrphans();

        int deleted = jdbcTemplate.update("""
                DELETE t FROM trip t
                WHERE NOT EXISTS (SELECT 1 FROM itinerary i WHERE i.trip_id = t.id AND i.deleted = 0)
                  AND NOT EXISTS (SELECT 1 FROM travel_note n WHERE n.trip_id = t.id AND n.deleted = 0)
                  AND NOT EXISTS (SELECT 1 FROM expense e WHERE e.trip_id = t.id AND e.deleted = 0)
                """);
        return new Result(itineraries.size(), merged, deleted);
    }

    /** 返回被合并掉的旅行数量。 */
    private int mergeCluster(List<Itinerary> cluster, Map<Long, Long> remap) {
        Long target = cluster.stream()
                .map(Itinerary::getTripId)
                .filter(id -> id != null)
                .min(Long::compareTo)
                .orElse(null);
        Long targetId;
        if (target == null) {
            Trip created = createTrip(cluster.get(0));
            targetId = created.getId();
        } else {
            targetId = target;
        }
        int merged = 0;
        for (Itinerary itinerary : cluster) {
            Long oldTrip = itinerary.getTripId();
            if (oldTrip != null && !oldTrip.equals(targetId)) {
                remap.put(oldTrip, targetId);
                merged++;
            }
            if (!targetId.equals(oldTrip)) {
                itinerary.setTripId(targetId);
                itineraryMapper.updateById(itinerary);
            }
        }
        tripMapper.recalculateDates(targetId);
        return merged;
    }

    private Trip createTrip(Itinerary first) {
        Trip trip = new Trip();
        trip.setUserId(first.getUserId());
        trip.setCountryCode(first.getCountryCode());
        trip.setName(catalogService.countryName(first.getCountryCode())
                + " · " + first.getStartDate().format(MONTH));
        trip.setStartDate(first.getStartDate());
        trip.setEndDate(first.getEndDate());
        tripMapper.insert(trip);
        return trip;
    }

    /** 未归组的游记/消费按日期重新匹配。 */
    private void assignOrphans() {
        List<TravelNote> notes = noteMapper.selectList(
                new LambdaQueryWrapper<TravelNote>().isNull(TravelNote::getTripId));
        for (TravelNote note : notes) {
            LocalDate date = note.getCreatedAt() == null
                    ? LocalDate.now() : note.getCreatedAt().toLocalDate();
            Trip trip = tripService.resolveForDate(note.getUserId(), note.getCountryCode(),
                    date, TripService.GROUPING_WINDOW_DAYS);
            if (trip != null) {
                note.setTripId(trip.getId());
                noteMapper.updateById(note);
            }
        }
        List<Expense> expenses = expenseMapper.selectList(
                new LambdaQueryWrapper<Expense>().isNull(Expense::getTripId));
        for (Expense expense : expenses) {
            Trip trip = tripService.resolveForDate(expense.getUserId(),
                    expense.getCountryCode(), expense.getExpenseDate(), 0);
            if (trip != null) {
                expense.setTripId(trip.getId());
                expenseMapper.updateById(expense);
            }
        }
    }
}