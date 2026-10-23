package com.travelmapper.service.map;

import com.travelmapper.controller.map.dto.CountryDrawerResponse;
import com.travelmapper.controller.map.dto.MapStatsResponse;
import com.travelmapper.service.catalog.CatalogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class MapService {

    private final JdbcTemplate jdbcTemplate;
    private final CatalogService catalogService;

    public MapService(JdbcTemplate jdbcTemplate, CatalogService catalogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.catalogService = catalogService;
    }

    public String statusFor(Long userId, String rawCountryCode) {
        String countryCode = rawCountryCode.toUpperCase(Locale.ROOT);
        Map<String, Object> counts = jdbcTemplate.queryForMap(
                "SELECT "
                        + "(SELECT COUNT(*) FROM itinerary WHERE user_id = ? AND country_code = ? AND deleted = 0) AS itineraries, "
                        + "(SELECT COUNT(*) FROM travel_note WHERE user_id = ? AND country_code = ? AND deleted = 0) AS notes",
                userId, countryCode, userId, countryCode);
        int itineraries = ((Number) counts.get("itineraries")).intValue();
        int notes = ((Number) counts.get("notes")).intValue();
        return notes > 0 ? "GREEN" : itineraries > 0 ? "YELLOW" : "GRAY";
    }

    public MapStatsResponse stats(Long userId) {
        Map<String, ItineraryAgg> itineraryByCountry = new TreeMap<>();
        jdbcTemplate.query(
                "SELECT country_code, COUNT(*) AS cnt, "
                        + "MAX(CASE WHEN end_date >= CURDATE() THEN 1 ELSE 0 END) AS unexpired "
                        + "FROM itinerary WHERE user_id = ? AND deleted = 0 AND status = 'SAVED' "
                        + "GROUP BY country_code",
                (rs, rowNum) -> {
                    itineraryByCountry.put(rs.getString("country_code"),
                            new ItineraryAgg(rs.getInt("cnt"), rs.getInt("unexpired") == 1));
                    return null;
                },
                userId);

        Map<String, Integer> notesByCountry = new TreeMap<>();
        jdbcTemplate.query(
                "SELECT country_code, COUNT(*) AS cnt FROM travel_note "
                        + "WHERE user_id = ? AND deleted = 0 GROUP BY country_code",
                (rs, rowNum) -> {
                    notesByCountry.put(rs.getString("country_code"), rs.getInt("cnt"));
                    return null;
                },
                userId);

        Map<String, String> names = new TreeMap<>();
        jdbcTemplate.query("SELECT code, name_zh FROM country",
                (rs, rowNum) -> {
                    names.put(rs.getString("code"), rs.getString("name_zh"));
                    return null;
                });

        List<String> codes = new ArrayList<>(itineraryByCountry.keySet());
        for (String code : notesByCountry.keySet()) {
            if (!codes.contains(code)) {
                codes.add(code);
            }
        }
        codes.sort(String::compareTo);

        List<MapStatsResponse.CountryStat> countries = new ArrayList<>();
        for (String code : codes) {
            int itineraryCount = itineraryByCountry.getOrDefault(code, new ItineraryAgg(0, false)).count();
            int noteCount = notesByCountry.getOrDefault(code, 0);
            if (itineraryCount == 0 && noteCount == 0) {
                continue;
            }
            boolean hasUnexpired = itineraryByCountry.getOrDefault(code, new ItineraryAgg(0, false)).hasUnexpired();
            String status = noteCount > 0 ? "GREEN" : "YELLOW";
            boolean expired = noteCount == 0 && itineraryCount > 0 && !hasUnexpired;
            countries.add(new MapStatsResponse.CountryStat(
                    code,
                    names.getOrDefault(code, code),
                    status,
                    itineraryCount,
                    noteCount,
                    itineraryCount + noteCount,
                    expired));
        }
        return new MapStatsResponse(countries);
    }

    public CountryDrawerResponse drawer(Long userId, String rawCountryCode) {
        String countryCode = rawCountryCode.toUpperCase(Locale.ROOT);
        String countryName = catalogService.countryName(countryCode);

        Map<String, Object> counts = jdbcTemplate.queryForMap(
                "SELECT "
                        + "(SELECT COUNT(*) FROM itinerary WHERE user_id = ? AND country_code = ? AND deleted = 0) AS itineraries, "
                        + "(SELECT COUNT(*) FROM travel_note WHERE user_id = ? AND country_code = ? AND deleted = 0) AS notes",
                userId, countryCode, userId, countryCode);
        int itineraryCount = ((Number) counts.get("itineraries")).intValue();
        int noteCount = ((Number) counts.get("notes")).intValue();

        Map<String, Object> expense = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(amount_cny), 0) AS total, "
                        + "COALESCE(SUM(CASE WHEN exchange_rate_snapshot IS NULL THEN 1 ELSE 0 END), 0) AS missing "
                        + "FROM expense WHERE user_id = ? AND country_code = ? AND deleted = 0",
                userId, countryCode);
        BigDecimal totalExpense = (BigDecimal) expense.get("total");
        int missingCount = ((Number) expense.get("missing")).intValue();
        List<String> unconfiguredCurrencies = jdbcTemplate.queryForList(
                "SELECT DISTINCT currency FROM expense WHERE user_id = ? AND country_code = ? "
                        + "AND deleted = 0 AND exchange_rate_snapshot IS NULL ORDER BY currency",
                String.class, userId, countryCode);

        List<TripGroupBuilder> trips = new ArrayList<>();
        List<TripRow> tripRows = jdbcTemplate.query(
                "SELECT id, name, start_date, end_date FROM trip "
                        + "WHERE user_id = ? AND country_code = ? AND deleted = 0 "
                        + "ORDER BY start_date DESC, created_at DESC",
                (rs, rowNum) -> new TripRow(
                        rs.getLong("id"),
                        rs.getString("name"),
                        toLocalDate(rs.getDate("start_date")),
                        toLocalDate(rs.getDate("end_date"))),
                userId, countryCode);

        for (TripRow trip : tripRows) {
            BigDecimal subtotal = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(amount_cny), 0) FROM expense "
                            + "WHERE user_id = ? AND trip_id = ? AND deleted = 0",
                    BigDecimal.class, userId, trip.id());
            TreeMap<String, CityGroupBuilder> cities = new TreeMap<>();

            List<ItineraryRow> itineraries = jdbcTemplate.query(
                    "SELECT id, title, start_date, end_date FROM itinerary "
                            + "WHERE user_id = ? AND trip_id = ? AND deleted = 0 ORDER BY start_date ASC",
                    (rs, rowNum) -> new ItineraryRow(
                            rs.getLong("id"),
                            rs.getString("title"),
                            toLocalDate(rs.getDate("start_date")),
                            toLocalDate(rs.getDate("end_date"))),
                    userId, trip.id());
            for (ItineraryRow itinerary : itineraries) {
                List<String> cityCodes = jdbcTemplate.queryForList(
                        "SELECT DISTINCT city_code FROM itinerary_day WHERE itinerary_id = ? ORDER BY city_code",
                        String.class, itinerary.id());
                for (String cityCode : cityCodes) {
                    cities.computeIfAbsent(cityCode, code -> new CityGroupBuilder(
                                    code, catalogService.cityName(countryCode, code)))
                            .itineraries.add(toItineraryItem(itinerary));
                }
            }

            List<NoteRow> notes = jdbcTemplate.query(
                    "SELECT id, title, visibility, created_at, city_code FROM travel_note "
                            + "WHERE user_id = ? AND trip_id = ? AND deleted = 0 ORDER BY created_at ASC",
                    (rs, rowNum) -> new NoteRow(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("visibility"),
                            rs.getTimestamp("created_at"),
                            rs.getString("city_code")),
                    userId, trip.id());
            for (NoteRow note : notes) {
                cities.computeIfAbsent(note.cityCode(), code -> new CityGroupBuilder(
                                code, catalogService.cityName(countryCode, code)))
                        .notes.add(toNoteItem(note));
            }

            List<CountryDrawerResponse.CityGroup> cityGroups = new ArrayList<>();
            for (CityGroupBuilder city : cities.values()) {
                String label = city.notes.isEmpty() ? "PLANNED" : "COMPLETED";
                BigDecimal cityExpense = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(amount_cny), 0) FROM expense "
                                + "WHERE user_id = ? AND trip_id = ? AND city_code = ? AND deleted = 0",
                        BigDecimal.class, userId, trip.id(), city.cityCode);
                cityGroups.add(new CountryDrawerResponse.CityGroup(
                        city.cityCode, city.cityName, label,
                        List.copyOf(city.itineraries), List.copyOf(city.notes),
                        money(cityExpense)));
            }
            trips.add(new TripGroupBuilder(trip, money(subtotal), cityGroups));
        }

        List<CountryDrawerResponse.UngroupedItinerary> ungroupedItineraries = new ArrayList<>();
        List<ItineraryRow> ungroupedRows = jdbcTemplate.query(
                "SELECT id, title, start_date, end_date FROM itinerary "
                        + "WHERE user_id = ? AND country_code = ? AND trip_id IS NULL AND deleted = 0 "
                        + "ORDER BY start_date DESC",
                (rs, rowNum) -> new ItineraryRow(
                        rs.getLong("id"),
                        rs.getString("title"),
                        toLocalDate(rs.getDate("start_date")),
                        toLocalDate(rs.getDate("end_date"))),
                userId, countryCode);
        for (ItineraryRow row : ungroupedRows) {
            List<String> cityCodes = jdbcTemplate.queryForList(
                    "SELECT DISTINCT city_code FROM itinerary_day WHERE itinerary_id = ? ORDER BY city_code",
                    String.class, row.id());
            List<CountryDrawerResponse.CityRef> cityRefs = cityCodes.stream()
                    .map(code -> new CountryDrawerResponse.CityRef(
                            code, catalogService.cityName(countryCode, code)))
                    .toList();
            ungroupedItineraries.add(new CountryDrawerResponse.UngroupedItinerary(
                    row.id(), row.title(), row.startDate(), row.endDate(),
                    row.endDate() != null && row.endDate().isBefore(LocalDate.now()), cityRefs));
        }

        List<CountryDrawerResponse.NoteItem> ungroupedNotes = jdbcTemplate.query(
                "SELECT id, title, visibility, created_at FROM travel_note "
                        + "WHERE user_id = ? AND country_code = ? AND trip_id IS NULL AND deleted = 0 "
                        + "ORDER BY created_at DESC",
                (rs, rowNum) -> new CountryDrawerResponse.NoteItem(
                        rs.getLong("id"), rs.getString("title"), rs.getString("visibility"),
                        toLocalDate(rs.getTimestamp("created_at"))),
                userId, countryCode);

        List<CountryDrawerResponse.ExpenseItem> ungroupedExpenses = jdbcTemplate.query(
                "SELECT id, currency, amount, expense_date, category FROM expense "
                        + "WHERE user_id = ? AND country_code = ? AND trip_id IS NULL AND deleted = 0 "
                        + "ORDER BY expense_date DESC, id DESC",
                (rs, rowNum) -> new CountryDrawerResponse.ExpenseItem(
                        rs.getLong("id"), rs.getString("currency"),
                        money(rs.getBigDecimal("amount")),
                        toLocalDate(rs.getDate("expense_date")),
                        rs.getString("category")),
                userId, countryCode);
        BigDecimal ungroupedExpense = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount_cny), 0) FROM expense "
                        + "WHERE user_id = ? AND country_code = ? AND trip_id IS NULL AND deleted = 0",
                BigDecimal.class, userId, countryCode);

        String status = noteCount > 0 ? "GREEN" : itineraryCount > 0 ? "YELLOW" : "GRAY";
        CountryDrawerResponse.Summary summary = new CountryDrawerResponse.Summary(
                itineraryCount, noteCount, money(totalExpense),
                missingCount > 0, unconfiguredCurrencies);
        List<CountryDrawerResponse.TripGroup> tripGroups = trips.stream()
                .map(trip -> new CountryDrawerResponse.TripGroup(
                        trip.trip.id(), trip.trip.name(), trip.trip.startDate(), trip.trip.endDate(),
                        trip.subtotalCny, trip.cities))
                .toList();
        CountryDrawerResponse.Ungrouped ungrouped = new CountryDrawerResponse.Ungrouped(
                ungroupedItineraries, ungroupedNotes, ungroupedExpenses, money(ungroupedExpense));

        return new CountryDrawerResponse(countryCode, countryName, status, summary, tripGroups, ungrouped);
    }

    private CountryDrawerResponse.ItineraryItem toItineraryItem(ItineraryRow itinerary) {
        return new CountryDrawerResponse.ItineraryItem(
                itinerary.id(), itinerary.title(), itinerary.startDate(), itinerary.endDate(),
                itinerary.endDate() != null && itinerary.endDate().isBefore(LocalDate.now()));
    }

    private CountryDrawerResponse.NoteItem toNoteItem(NoteRow note) {
        return new CountryDrawerResponse.NoteItem(
                note.id(), note.title(), note.visibility(),
                note.createdAt() == null ? null : note.createdAt().toLocalDateTime().toLocalDate());
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private LocalDate toLocalDate(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toLocalDate();
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record ItineraryAgg(int count, boolean hasUnexpired) {
    }

    private record TripRow(Long id, String name, LocalDate startDate, LocalDate endDate) {
    }

    private record ItineraryRow(Long id, String title, LocalDate startDate, LocalDate endDate) {
    }

    private record NoteRow(Long id, String title, String visibility, Timestamp createdAt, String cityCode) {
    }

    private static final class CityGroupBuilder {
        private final String cityCode;
        private final String cityName;
        private final List<CountryDrawerResponse.ItineraryItem> itineraries = new ArrayList<>();
        private final List<CountryDrawerResponse.NoteItem> notes = new ArrayList<>();

        private CityGroupBuilder(String cityCode, String cityName) {
            this.cityCode = cityCode;
            this.cityName = cityName;
        }
    }

    private record TripGroupBuilder(TripRow trip, String subtotalCny,
                                    List<CountryDrawerResponse.CityGroup> cities) {
    }
}
