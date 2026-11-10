package com.travelmapper.service.expense;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.controller.expense.dto.ExpenseDtos;
import com.travelmapper.repository.entity.ExchangeRate;
import com.travelmapper.repository.entity.Expense;
import com.travelmapper.repository.entity.Itinerary;
import com.travelmapper.repository.entity.ItineraryDay;
import com.travelmapper.repository.entity.Trip;
import com.travelmapper.repository.enums.ExpenseCategory;
import com.travelmapper.repository.mapper.ExchangeRateMapper;
import com.travelmapper.repository.mapper.ExpenseMapper;
import com.travelmapper.repository.mapper.ExpenseMapper.SummaryRow;
import com.travelmapper.repository.mapper.ItineraryDayMapper;
import com.travelmapper.repository.mapper.ItineraryMapper;
import com.travelmapper.repository.mapper.TripMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.travelmapper.service.catalog.CatalogService;
import com.travelmapper.service.trip.TripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ExpenseService {

    /** 分组维度到 SQL 列的映射，仅内部白名单，防止拼接注入。 */
    private static final Map<String, String> SUMMARY_GROUP_COLUMNS = Map.of(
            "country", "country_code",
            "city", "city_code",
            "trip", "trip_id",
            "date", "expense_date");

    private final ExpenseMapper expenseMapper;
    private final ExchangeRateMapper rateMapper;
    private final ItineraryMapper itineraryMapper;
    private final ItineraryDayMapper itineraryDayMapper;
    private final TripMapper tripMapper;
    private final TripService tripService;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;

    public ExpenseService(ExpenseMapper expenseMapper, ExchangeRateMapper rateMapper,
                          ItineraryMapper itineraryMapper, ItineraryDayMapper itineraryDayMapper,
                          TripMapper tripMapper, TripService tripService,
                          CatalogService catalogService, AuditLogService auditLogService,
                          TimeProvider timeProvider) {
        this.expenseMapper = expenseMapper;
        this.rateMapper = rateMapper;
        this.itineraryMapper = itineraryMapper;
        this.itineraryDayMapper = itineraryDayMapper;
        this.tripMapper = tripMapper;
        this.tripService = tripService;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
        this.timeProvider = timeProvider;
    }

    @Transactional
    public ExpenseDtos.CreateResponse create(Long userId, ExpenseDtos.CreateRequest request) {
        ResolvedContext context = resolveContext(userId, request.getItineraryId(),
                request.getCountryCode(), request.getCityCode());
        String currency = normalizeCurrency(request.getCurrency());
        BigDecimal amount = parseAmount(request.getAmount());
        String category = normalizeCategory(request.getCategory());
        validateDate(request.getExpenseDate());
        validateRemark(request.getRemark());
        Long tripId = request.getItineraryId() != null ? context.tripId() : request.getTripId();
        boolean tripInherited = request.getItineraryId() != null && tripId != null;
        if (request.getItineraryId() == null && tripId != null) {
            Trip trip = tripService.requireOwnedTrip(userId, tripId);
            if (!trip.getCountryCode().equals(context.countryCode())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "旅行国家与消费不一致");
            }
        }
        if (request.getItineraryId() == null && tripId == null) {
            // 未指定归档时按消费日期匹配；没有归档就新建。
            tripId = tripService.resolveOrCreateForDate(
                    userId, context.countryCode(), request.getExpenseDate()).getId();
        }
        BigDecimal snapshot = findRate(currency);
        Expense expense = new Expense();
        expense.setUserId(userId);
        expense.setTripId(tripId);
        expense.setCountryCode(context.countryCode());
        expense.setCityCode(context.cityCode());
        expense.setCurrency(currency);
        expense.setAmount(amount);
        expense.setExchangeRateSnapshot(snapshot);
        expense.setCategory(ExpenseCategory.valueOf(category));
        expense.setExpenseDate(request.getExpenseDate());
        expense.setRemark(request.getRemark());
        expenseMapper.insert(expense);
        Expense saved = expenseMapper.selectById(expense.getId());
        auditLogService.record(userId, "EXPENSE_CREATE", "EXPENSE", saved.getId(),
                Map.of("category", saved.getCategory().name(), "currency", saved.getCurrency()), null);
        return toCreateResponse(saved, tripInherited);
    }

    @Transactional
    public ExpenseDtos.CreateResponse update(Long userId, Long id, ExpenseDtos.UpdateRequest request) {
        Expense expense = requireOwned(userId, id);
        boolean recalcRate = false;
        List<String> changed = new ArrayList<>();
        if (request.getItineraryId() != null) {
            ResolvedContext context = resolveContext(userId, request.getItineraryId(),
                    null, request.getCityCode());
            expense.setCountryCode(context.countryCode());
            expense.setCityCode(context.cityCode());
            expense.setTripId(context.tripId());
            changed.add("trip");
        } else {
            if (request.getCountryCode() != null) {
                String countryCode = request.getCountryCode().toUpperCase(Locale.ROOT);
                catalogService.requireEnabledCountry(countryCode);
                expense.setCountryCode(countryCode);
                changed.add("country");
            }
            if (request.getCityCode() != null) {
                String cityCode = request.getCityCode().toUpperCase(Locale.ROOT);
                catalogService.requireEnabledCity(expense.getCountryCode(), cityCode);
                expense.setCityCode(cityCode);
                changed.add("city");
            }
            if (request.getTripId() != null) {
                Trip trip = tripService.requireOwnedTrip(userId, request.getTripId());
                if (!trip.getCountryCode().equals(expense.getCountryCode())) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "旅行国家与消费不一致");
                }
                expense.setTripId(request.getTripId());
                changed.add("trip");
            }
        }
        if (request.getCurrency() != null) {
            expense.setCurrency(normalizeCurrency(request.getCurrency()));
            recalcRate = true;
            changed.add("currency");
        }
        if (request.getAmount() != null) {
            expense.setAmount(parseAmount(request.getAmount()));
            recalcRate = true;
            changed.add("amount");
        }
        if (recalcRate) {
            expense.setExchangeRateSnapshot(findRate(expense.getCurrency()));
        }
        if (request.getCategory() != null) {
            expense.setCategory(ExpenseCategory.valueOf(normalizeCategory(request.getCategory())));
            changed.add("category");
        }
        if (request.getExpenseDate() != null) {
            validateDate(request.getExpenseDate());
            expense.setExpenseDate(request.getExpenseDate());
            changed.add("date");
        }
        if (request.getRemark() != null) {
            validateRemark(request.getRemark());
            expense.setRemark(request.getRemark());
            changed.add("remark");
        }
        expenseMapper.updateById(expense);
        auditLogService.record(userId, "EXPENSE_UPDATE", "EXPENSE", id,
                Map.of("changed", changed), null);
        return toCreateResponse(expenseMapper.selectById(id), false);
    }

    @Transactional
    public ExpenseDtos.DeleteResponse delete(Long userId, Long id) {
        Expense expense = requireOwned(userId, id);
        expenseMapper.deleteById(expense.getId());
        boolean tripDeleted = tripService.deleteIfEmpty(expense.getTripId());
        auditLogService.record(userId, "EXPENSE_DELETE", "EXPENSE", id,
                Map.of("tripDeleted", tripDeleted), null);
        return new ExpenseDtos.DeleteResponse(id, tripDeleted);
    }

    public PageResult<ExpenseDtos.Response> list(Long userId, String countryCode, String cityCode,
                                                 Long tripId, Boolean ungrouped, String category,
                                                 LocalDate from, LocalDate to, PageRequest request) {
        request.validate();
        LambdaQueryWrapper<Expense> wrapper = new LambdaQueryWrapper<Expense>()
                .eq(Expense::getUserId, userId)
                .eq(countryCode != null && !countryCode.isBlank(), Expense::getCountryCode,
                        countryCode == null ? null : countryCode.toUpperCase(Locale.ROOT))
                .eq(cityCode != null && !cityCode.isBlank(), Expense::getCityCode,
                        cityCode == null ? null : cityCode.toUpperCase(Locale.ROOT))
                .eq(tripId != null, Expense::getTripId, tripId)
                .isNull(Boolean.TRUE.equals(ungrouped), Expense::getTripId)
                .eq(category != null && !category.isBlank(), Expense::getCategory,
                        category == null ? null : ExpenseCategory.valueOf(normalizeCategory(category)))
                .ge(from != null, Expense::getExpenseDate, from)
                .le(to != null, Expense::getExpenseDate, to)
                .orderByDesc(Expense::getExpenseDate)
                .orderByDesc(Expense::getId);
        Page<Expense> page = expenseMapper.selectPage(new Page<>(request.getPage(), request.getSize()), wrapper);
        List<ExpenseDtos.Response> rows = page.getRecords().stream().map(this::toResponse).toList();
        return PageResult.of(page.getTotal(), request, rows);
    }

    public ExpenseDtos.DateGroupedResponse byDate(Long userId, String countryCode, String cityCode,
                                              Long tripId, Boolean ungrouped, String category,
                                              LocalDate from, LocalDate to) {
        LambdaQueryWrapper<Expense> wrapper = new LambdaQueryWrapper<Expense>()
                .eq(Expense::getUserId, userId)
                .eq(countryCode != null && !countryCode.isBlank(), Expense::getCountryCode,
                        countryCode == null ? null : countryCode.toUpperCase(Locale.ROOT))
                .eq(cityCode != null && !cityCode.isBlank(), Expense::getCityCode,
                        cityCode == null ? null : cityCode.toUpperCase(Locale.ROOT))
                .eq(tripId != null, Expense::getTripId, tripId)
                .isNull(Boolean.TRUE.equals(ungrouped), Expense::getTripId)
                .eq(category != null && !category.isBlank(), Expense::getCategory,
                        category == null ? null : ExpenseCategory.valueOf(normalizeCategory(category)))
                .ge(from != null, Expense::getExpenseDate, from)
                .le(to != null, Expense::getExpenseDate, to)
                .orderByDesc(Expense::getExpenseDate)
                .orderByDesc(Expense::getId);
        List<Expense> rows = expenseMapper.selectList(wrapper);

        Map<LocalDate, List<ExpenseDtos.Response>> itemsByDate = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> totalByDate = new LinkedHashMap<>();
        Map<LocalDate, TreeMap<String, BigDecimal>> originalsByDate = new LinkedHashMap<>();
        java.util.Set<String> unconfigured = new java.util.LinkedHashSet<>();
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Expense expense : rows) {
            ExpenseDtos.Response response = toResponse(expense);
            LocalDate date = expense.getExpenseDate();
            itemsByDate.computeIfAbsent(date, key -> new ArrayList<>()).add(response);
            originalsByDate.computeIfAbsent(date, key -> new TreeMap<>())
                    .merge(expense.getCurrency(), expense.getAmount(), BigDecimal::add);
            if (response.amountCny() != null) {
                BigDecimal amountCny = new BigDecimal(response.amountCny());
                totalByDate.merge(date, amountCny, BigDecimal::add);
                grandTotal = grandTotal.add(amountCny);
            } else {
                unconfigured.add(expense.getCurrency());
            }
        }

        List<ExpenseDtos.DateGroup> groups = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ExpenseDtos.Response>> entry : itemsByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<ExpenseDtos.CurrencyTotal> originals = originalsByDate.get(date).entrySet().stream()
                    .map(item -> new ExpenseDtos.CurrencyTotal(
                            item.getKey(), item.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString()))
                    .toList();
            groups.add(new ExpenseDtos.DateGroup(
                    date,
                    totalByDate.getOrDefault(date, BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    originals,
                    entry.getValue()));
        }
        return new ExpenseDtos.DateGroupedResponse(
                grandTotal.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                !unconfigured.isEmpty(),
                new ArrayList<>(unconfigured),
                groups);
    }

    public ExpenseDtos.SummaryResponse summary(Long userId, String groupByCsv, String countryCode,
                                               String cityCode, Long tripId, String category,
                                               LocalDate from, LocalDate to) {
        List<String> groupBy = parseGroupBy(groupByCsv);
        String country = countryCode == null || countryCode.isBlank()
                ? null : countryCode.toUpperCase(Locale.ROOT);
        String city = cityCode == null || cityCode.isBlank()
                ? null : cityCode.toUpperCase(Locale.ROOT);
        String categoryValue = null;
        if (category != null && !category.isBlank()) {
            try {
                categoryValue = ExpenseCategory.valueOf(category.toUpperCase(Locale.ROOT)).name();
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "未知消费分类");
            }
        }
        List<String> groupColumns = groupBy.stream().map(SUMMARY_GROUP_COLUMNS::get).toList();

        List<ExpenseMapper.SummaryRow> rows = expenseMapper.selectSummaryGroups(
                userId, groupColumns, country, city, tripId, categoryValue, from, to);
        List<String> unconfigured = expenseMapper.selectUnconfiguredCurrencies(
                userId, country, city, tripId, categoryValue, from, to);

        Map<Long, String> tripNames = new LinkedHashMap<>();
        if (groupBy.contains("trip")) {
            for (Trip trip : tripMapper.selectList(
                    new LambdaQueryWrapper<Trip>().eq(Trip::getUserId, userId))) {
                tripNames.put(trip.getId(), trip.getName());
            }
        }
        Map<String, String> countryNames = new LinkedHashMap<>();
        Map<String, String> cityNames = new LinkedHashMap<>();

        TreeMap<String, GroupAccumulator> groups = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ExpenseMapper.SummaryRow row : rows) {
            if (row.getTotalCny() != null) {
                total = total.add(row.getTotalCny());
            }
            String key = groupKey(row, groupBy);
            GroupAccumulator accumulator = groups.computeIfAbsent(key,
                    ignored -> new GroupAccumulator(
                            groupLabel(row, groupBy, tripNames, countryNames, cityNames)));
            if (row.getTotalCny() != null) {
                accumulator.total = accumulator.total.add(row.getTotalCny());
                accumulator.categoryTotals.merge(ExpenseCategory.valueOf(row.getCategory()),
                        row.getTotalCny(), BigDecimal::add);
            }
            if (groupBy.contains("country") || groupBy.contains("trip")) {
                accumulator.currencyTotals.merge(row.getCurrency(),
                        row.getTotalOriginal(), BigDecimal::add);
            }
        }

        List<ExpenseDtos.Group> result = new ArrayList<>();
        for (Map.Entry<String, GroupAccumulator> entry : groups.entrySet()) {
            GroupAccumulator accumulator = entry.getValue();
            List<ExpenseDtos.CategoryTotal> categories = accumulator.categoryTotals.entrySet().stream()
                    .sorted(Comparator.comparing(item -> item.getKey().ordinal()))
                    .map(item -> new ExpenseDtos.CategoryTotal(
                            item.getKey().name(), money(item.getValue())))
                    .toList();
            List<ExpenseDtos.CurrencyTotal> currencies = accumulator.currencyTotals.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(item -> new ExpenseDtos.CurrencyTotal(item.getKey(), money(item.getValue())))
                    .toList();
            result.add(new ExpenseDtos.Group(
                    entry.getKey(), accumulator.label, money(accumulator.total),
                    categories, currencies));
        }
        return new ExpenseDtos.SummaryResponse(
                groupBy, "CNY", money(total), !unconfigured.isEmpty(),
                unconfigured, result);
    }

    private ExpenseDtos.Response toResponse(Expense expense) {
        String countryName = catalogService.countryName(expense.getCountryCode());
        String cityName = expense.getCityCode() == null ? null
                : catalogService.cityName(expense.getCountryCode(), expense.getCityCode());
        return new ExpenseDtos.Response(
                expense.getId(),
                expense.getCountryCode(),
                countryName,
                expense.getCityCode(),
                cityName,
                expense.getTripId(),
                expense.getCurrency(),
                money(expense.getAmount()),
                expense.getExchangeRateSnapshot() == null ? null
                        : expense.getExchangeRateSnapshot().setScale(6, RoundingMode.HALF_UP).toPlainString(),
                expense.getAmountCny() == null ? null : money(expense.getAmountCny()),
                expense.getExchangeRateSnapshot() == null,
                expense.getCategory().name(),
                expense.getExpenseDate(),
                expense.getRemark(),
                expense.getCreatedAt());
    }

    private ExpenseDtos.CreateResponse toCreateResponse(Expense expense, boolean tripInherited) {
        return new ExpenseDtos.CreateResponse(
                expense.getId(),
                expense.getCurrency(),
                money(expense.getAmount()),
                expense.getExchangeRateSnapshot() == null ? null
                        : expense.getExchangeRateSnapshot().setScale(6, RoundingMode.HALF_UP).toPlainString(),
                expense.getAmountCny() == null ? null : money(expense.getAmountCny()),
                expense.getExchangeRateSnapshot() == null,
                expense.getTripId(),
                tripInherited);
    }

    private ResolvedContext resolveContext(Long userId, Long itineraryId, String rawCountryCode,
                                           String rawCityCode) {
        if (itineraryId != null) {
            Itinerary itinerary = itineraryMapper.selectById(itineraryId);
            if (itinerary == null || !userId.equals(itinerary.getUserId())) {
                throw new BusinessException(ErrorCode.ITINERARY_NOT_FOUND);
            }
            String cityCode = rawCityCode == null || rawCityCode.isBlank() ? null
                    : rawCityCode.toUpperCase(Locale.ROOT);
            if (cityCode == null) {
                List<ItineraryDay> days = itineraryDayMapper.selectByItinerary(itineraryId);
                cityCode = days.isEmpty() ? null : days.get(0).getCityCode();
            }
            if (cityCode != null) {
                catalogService.requireEnabledCity(itinerary.getCountryCode(), cityCode);
            }
            return new ResolvedContext(itinerary.getCountryCode(), cityCode, itinerary.getTripId());
        }
        String countryCode = rawCountryCode == null ? "" : rawCountryCode.toUpperCase(Locale.ROOT);
        catalogService.requireEnabledCountry(countryCode);
        String cityCode = rawCityCode == null || rawCityCode.isBlank() ? null
                : rawCityCode.toUpperCase(Locale.ROOT);
        if (cityCode != null) {
            catalogService.requireEnabledCity(countryCode, cityCode);
        }
        return new ResolvedContext(countryCode, cityCode, null);
    }

    private Expense requireOwned(Long userId, Long id) {
        Expense expense = expenseMapper.selectById(id);
        if (expense == null || !userId.equals(expense.getUserId())) {
            throw new BusinessException(ErrorCode.EXPENSE_NOT_FOUND);
        }
        return expense;
    }

    private BigDecimal findRate(String currency) {
        ExchangeRate rate = rateMapper.selectOne(new LambdaQueryWrapper<ExchangeRate>()
                .eq(ExchangeRate::getCurrency, currency));
        return rate == null ? null : rate.getRateToCny();
    }

    private String normalizeCurrency(String currency) {
        String value = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z]{3}")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "币种必须为 3 位大写字母");
        }
        return value;
    }

    private BigDecimal parseAmount(String amount) {
        try {
            BigDecimal value = new BigDecimal(amount);
            if (value.scale() > 2) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "金额最多两位小数");
            }
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "金额格式非法");
        }
    }

    private String normalizeCategory(String category) {
        try {
            return ExpenseCategory.valueOf(category.trim().toUpperCase(Locale.ROOT)).name();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分类非法");
        }
    }

    private void validateDate(LocalDate date) {
        if (date == null || date.isAfter(timeProvider.today())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "消费日期不得晚于今天");
        }
    }

    private void validateRemark(String remark) {
        if (remark != null && remark.length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "备注最多 100 字");
        }
    }

    private List<String> parseGroupBy(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of("country");
        }
        List<String> result = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (!List.of("country", "city", "trip", "date").contains(value)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "groupBy 非法");
            }
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return result.isEmpty() ? List.of("country") : result;
    }

    private String groupKey(SummaryRow row, List<String> groupBy) {
        List<String> parts = new ArrayList<>();
        for (String dimension : groupBy) {
            switch (dimension) {
                case "country" -> parts.add(row.getCountryCode());
                case "city" -> parts.add(row.getCityCode() == null ? "" : row.getCityCode());
                case "trip" -> parts.add(row.getTripId() == null ? "0" : String.valueOf(row.getTripId()));
                case "date" -> parts.add(row.getExpenseDate().toString());
                default -> {
                }
            }
        }
        return String.join("|", parts);
    }

    private String groupLabel(SummaryRow row, List<String> groupBy, Map<Long, String> tripNames,
                              Map<String, String> countryNames, Map<String, String> cityNames) {
        List<String> labels = new ArrayList<>();
        for (String dimension : groupBy) {
            switch (dimension) {
                case "country" -> labels.add(countryName(row.getCountryCode(), countryNames));
                case "city" -> labels.add(row.getCityCode() == null ? "未指定城市"
                        : cityName(row.getCountryCode(), row.getCityCode(), cityNames));
                case "trip" -> labels.add(row.getTripId() == null ? "未归组"
                        : tripNames.getOrDefault(row.getTripId(), "未归组"));
                case "date" -> labels.add(row.getExpenseDate().toString());
                default -> {
                }
            }
        }
        return String.join(" | ", labels);
    }

    /** 分组数量很少（国家/城市为个位数），按 key 缓存避免逐行查目录。 */
    private String countryName(String countryCode, Map<String, String> cache) {
        return cache.computeIfAbsent(countryCode, catalogService::countryName);
    }

    private String cityName(String countryCode, String cityCode, Map<String, String> cache) {
        return cache.computeIfAbsent(countryCode + "|" + cityCode,
                ignored -> catalogService.cityName(countryCode, cityCode));
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record ResolvedContext(String countryCode, String cityCode, Long tripId) {
    }

    private static final class GroupAccumulator {
        private final String label;
        private BigDecimal total = BigDecimal.ZERO;
        private final Map<ExpenseCategory, BigDecimal> categoryTotals = new LinkedHashMap<>();
        private final Map<String, BigDecimal> currencyTotals = new LinkedHashMap<>();

        private GroupAccumulator(String label) {
            this.label = label;
        }
    }
}
