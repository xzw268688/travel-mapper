package com.travelmapper.service.itinerary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.controller.itinerary.dto.GenerateItineraryResponse;
import com.travelmapper.controller.itinerary.dto.ItineraryDtos;
import com.travelmapper.repository.entity.City;
import com.travelmapper.repository.entity.Itinerary;
import com.travelmapper.repository.entity.ItineraryDay;
import com.travelmapper.repository.entity.ItinerarySpot;
import com.travelmapper.repository.entity.Trip;
import com.travelmapper.repository.enums.EnabledStatus;
import com.travelmapper.repository.enums.ItineraryStatus;
import com.travelmapper.repository.enums.SpotSource;
import com.travelmapper.repository.mapper.ItineraryDayMapper;
import com.travelmapper.repository.mapper.ItineraryMapper;
import com.travelmapper.repository.mapper.ItinerarySpotMapper;
import com.travelmapper.repository.mapper.TripMapper;
import com.travelmapper.service.catalog.CatalogService;
import com.travelmapper.service.map.MapService;
import com.travelmapper.service.quota.QuotaDeduction;
import com.travelmapper.service.quota.QuotaService;
import com.travelmapper.service.search.GuideLink;
import com.travelmapper.service.search.GuideLinkSanitizer;
import com.travelmapper.service.trip.TripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ItineraryService {

    private final ItineraryMapper itineraryMapper;
    private final ItineraryDayMapper dayMapper;
    private final ItinerarySpotMapper spotMapper;
    private final TripMapper tripMapper;
    private final TripService tripService;
    private final CatalogService catalogService;
    private final MapService mapService;
    private final GuideLinkSanitizer guideLinkSanitizer;
    private final ObjectMapper objectMapper;
    private final QuotaService quotaService;
    private final ItineraryGenerationService generationService;

    public ItineraryService(ItineraryMapper itineraryMapper, ItineraryDayMapper dayMapper,
                            ItinerarySpotMapper spotMapper, TripMapper tripMapper,
                            TripService tripService, CatalogService catalogService,
                            MapService mapService, GuideLinkSanitizer guideLinkSanitizer,
                            ObjectMapper objectMapper, QuotaService quotaService,
                            ItineraryGenerationService generationService) {
        this.itineraryMapper = itineraryMapper;
        this.dayMapper = dayMapper;
        this.spotMapper = spotMapper;
        this.tripMapper = tripMapper;
        this.tripService = tripService;
        this.catalogService = catalogService;
        this.mapService = mapService;
        this.guideLinkSanitizer = guideLinkSanitizer;
        this.objectMapper = objectMapper;
        this.quotaService = quotaService;
        this.generationService = generationService;
    }

    @Transactional
    public ItineraryDtos.SaveResponse save(Long userId, ItineraryDtos.SaveRequest request) {
        String countryCode = request.getCountryCode().toUpperCase(Locale.ROOT);
        catalogService.requireEnabledCountry(countryCode);
        if (request.getTitle() == null || request.getTitle().isBlank()
                || request.getTitle().length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "标题必须为 1-50 字");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
        long expectedDays = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
        if (request.getDays().size() != expectedDays) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "天数与日期区间不一致");
        }
        List<ItineraryDtos.DayRequest> days = new ArrayList<>(request.getDays());
        days.sort(Comparator.comparingInt(ItineraryDtos.DayRequest::getDayIndex));
        int manualTotal = 0;
        for (int i = 0; i < days.size(); i++) {
            ItineraryDtos.DayRequest day = days.get(i);
            if (day.getDayIndex() != i + 1) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "dayIndex 必须从 1 连续递增");
            }
            if (day.getDate() == null
                    || day.getDate().isBefore(request.getStartDate())
                    || day.getDate().isAfter(request.getEndDate())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "行程日期超出范围");
            }
            catalogService.requireEnabledCity(countryCode, day.getCityCode().toUpperCase(Locale.ROOT));
            List<ItineraryDtos.SpotRequest> spots = day.getSpots() == null ? List.of() : day.getSpots();
            if (spots.size() > 10) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "每天最多 10 个景点");
            }
            for (ItineraryDtos.SpotRequest spot : spots) {
                if (spot.getName() == null || spot.getName().isBlank()) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "景点名称必填");
                }
                if ("MANUAL".equalsIgnoreCase(spot.getSource())) {
                    manualTotal++;
                }
            }
        }
        if (manualTotal > 30) {
            throw new BusinessException(ErrorCode.MANUAL_SPOT_LIMIT);
        }
        List<GuideLink> guides = guideLinkSanitizer.sanitize(request.getGuides());
        boolean tripCreated = request.getTripId() == null;
        Trip trip = tripService.attachOrCreate(userId, request.getTripId(), countryCode,
                request.getTripName(), request.getStartDate(), request.getEndDate());

        Itinerary itinerary = new Itinerary();
        itinerary.setUserId(userId);
        itinerary.setTripId(trip.getId());
        itinerary.setCountryCode(countryCode);
        itinerary.setTitle(request.getTitle());
        itinerary.setStartDate(request.getStartDate());
        itinerary.setEndDate(request.getEndDate());
        itinerary.setGuidesJson(guides.isEmpty() ? null : toJson(guides));
        itinerary.setPreparationsJson(request.getPreparations() == null
                ? null : toJson(request.getPreparations()));
        itinerary.setStatus(ItineraryStatus.SAVED);
        itineraryMapper.insert(itinerary);
        insertDays(itinerary.getId(), days, countryCode);
        tripService.recalculateDates(trip.getId());
        return new ItineraryDtos.SaveResponse(
                itinerary.getId(), trip.getId(), trip.getName(), tripCreated,
                mapService.statusFor(userId, countryCode),
                spotMapper.countManual(itinerary.getId()));
    }

    public ItineraryDtos.Response detail(Long userId, Long id) {
        return toResponse(requireOwned(userId, id));
    }

    @Transactional
    public ItineraryDtos.Response update(Long userId, Long id, ItineraryDtos.UpdateRequest request) {
        Itinerary itinerary = requireOwned(userId, id);
        if (request.getTitle() != null) {
            if (request.getTitle().isBlank() || request.getTitle().length() > 50) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "标题必须为 1-50 字");
            }
            itinerary.setTitle(request.getTitle());
        }
        List<ItineraryDay> days = dayMapper.selectByItinerary(id);
        LocalDate oldStart = itinerary.getStartDate();
        LocalDate newStart = request.getStartDate() == null ? oldStart : request.getStartDate();
        long delta = ChronoUnit.DAYS.between(oldStart, newStart);
        LocalDate newEnd;
        if (request.getEndDate() != null) {
            newEnd = request.getEndDate();
            if (ChronoUnit.DAYS.between(newStart, newEnd) + 1 != days.size()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "编辑日期不能改变天数");
            }
        } else {
            newEnd = itinerary.getEndDate().plusDays(delta);
        }
        if (newEnd.isBefore(newStart)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        if (delta != 0) {
            for (ItineraryDay day : days) {
                day.setVisitDate(day.getVisitDate().plusDays(delta));
                dayMapper.updateById(day);
            }
        }
        itinerary.setStartDate(newStart);
        itinerary.setEndDate(newEnd);
        itineraryMapper.updateById(itinerary);
        if (itinerary.getTripId() != null) {
            tripService.recalculateDates(itinerary.getTripId());
        }
        return toResponse(requireOwned(userId, id));
    }

    @Transactional
    public ItineraryDtos.DeleteResponse delete(Long userId, Long id) {
        Itinerary itinerary = requireOwned(userId, id);
        itineraryMapper.deleteById(id);
        if (itinerary.getTripId() != null) {
            tripService.recalculateDates(itinerary.getTripId());
        }
        return new ItineraryDtos.DeleteResponse(id,
                mapService.statusFor(userId, itinerary.getCountryCode()));
    }

    @Transactional
    public ItineraryDtos.RegenerateResponse regenerate(Long userId, Long id,
                                                       ItineraryDtos.RegenerateRequest request) {
        Itinerary itinerary = requireOwned(userId, id);
        int manualCount = spotMapper.countManual(id);
        if (request.isKeepManualSpots() && manualCount >= 30) {
            throw new BusinessException(ErrorCode.MANUAL_SPOT_LIMIT);
        }
        List<ItineraryDay> oldDays = dayMapper.selectByItinerary(id);
        List<String> cityCodes = oldDays.stream()
                .map(ItineraryDay::getCityCode)
                .distinct()
                .toList();
        if (cityCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "行程没有城市");
        }
        QuotaDeduction deduction = quotaService.deduct(userId, request.getRequestId());
        try {
            GeneratedPlan plan = generationService.generatePlan(
                    itinerary.getCountryCode(), cityCodes, oldDays.size(),
                    itinerary.getStartDate(), request.getPreference());

            Map<Long, ItineraryDay> oldDayById = new LinkedHashMap<>();
            for (ItineraryDay day : oldDays) {
                oldDayById.put(day.getId(), day);
            }
            Map<Integer, GenerateItineraryResponse.DayPlan> planDayByIndex = new LinkedHashMap<>();
            for (GenerateItineraryResponse.DayPlan day : plan.days()) {
                planDayByIndex.put(day.dayIndex(), day);
            }
            List<ItinerarySpot> manualSpots = request.isKeepManualSpots()
                    ? spotMapper.selectManual(id) : List.of();
            Map<Integer, List<ItinerarySpot>> merged = new LinkedHashMap<>();
            List<String> mergeWarnings = new ArrayList<>();
            for (ItinerarySpot manual : manualSpots) {
                ItineraryDay oldDay = oldDayById.get(manual.getDayId());
                if (oldDay == null) {
                    continue;
                }
                GenerateItineraryResponse.DayPlan target = planDayByIndex.get(oldDay.getDayIndex());
                if (target == null) {
                    target = plan.days().get(0);
                    mergeWarnings.add("手动景点「" + manual.getName()
                            + "」原属第 " + oldDay.getDayIndex() + " 天，因新行程天数减少，已移至第 1 天");
                } else if (!target.cityCode().equals(oldDay.getCityCode())) {
                    mergeWarnings.add("手动景点「" + manual.getName() + "」原属城市"
                            + catalogService.cityName(itinerary.getCountryCode(), oldDay.getCityCode())
                            + "，已合并到城市"
                            + catalogService.cityName(itinerary.getCountryCode(), target.cityCode())
                            + "的第 " + target.dayIndex() + " 天，请确认");
                }
                merged.computeIfAbsent(target.dayIndex(), key -> new ArrayList<>()).add(manual);
            }

            spotMapper.deleteByItineraryIdPhysically(id);
            dayMapper.deleteByItineraryIdPhysically(id);
            for (GenerateItineraryResponse.DayPlan planDay : plan.days()) {
                ItineraryDay day = new ItineraryDay();
                day.setItineraryId(id);
                day.setDayIndex(planDay.dayIndex());
                day.setVisitDate(planDay.date());
                day.setCityCode(planDay.cityCode());
                dayMapper.insert(day);
                int order = 0;
                for (GenerateItineraryResponse.SpotPlan spot : planDay.spots()) {
                    insertSpot(day.getId(), order++, SpotSource.AI, spot.name(),
                            spot.description(), spot.suggestedDuration());
                }
                for (ItinerarySpot manual : merged.getOrDefault(planDay.dayIndex(), List.of())) {
                    insertSpot(day.getId(), order++, SpotSource.MANUAL, manual.getName(),
                            manual.getDescription(), manual.getSuggestedDuration());
                }
            }
            itinerary.setPreparationsJson(toJson(plan.preparations()));
            itinerary.setEndDate(itinerary.getStartDate().plusDays(plan.days().size() - 1L));
            itineraryMapper.updateById(itinerary);
            if (itinerary.getTripId() != null) {
                tripService.recalculateDates(itinerary.getTripId());
            }
            ItineraryDtos.Response response = toResponse(requireOwned(userId, id));
            return new ItineraryDtos.RegenerateResponse(
                    response,
                    manualSpots.size(),
                    mergeWarnings,
                    plan.warnings(),
                    new GenerateItineraryResponse.QuotaInfo(
                            deduction.source(), deduction.freeUsedToday(),
                            deduction.freeLimit(), deduction.packBalance()));
        } catch (RuntimeException ex) {
            quotaService.refund(userId, request.getRequestId());
            throw ex;
        }
    }

    @Transactional
    public ItineraryDtos.AddSpotResponse addSpot(Long userId, Long itineraryId, Long dayId,
                                                 ItineraryDtos.AddSpotRequest request) {
        Itinerary itinerary = requireOwned(userId, itineraryId);
        ItineraryDay day = requireDay(itineraryId, dayId);
        if (spotMapper.countManual(itineraryId) >= 30) {
            throw new BusinessException(ErrorCode.MANUAL_SPOT_LIMIT);
        }
        List<ItinerarySpot> spots = spotMapper.selectByDay(dayId);
        int index = request.getInsertIndex() == null
                ? spots.size() : Math.max(0, Math.min(spots.size(), request.getInsertIndex()));
        for (ItinerarySpot spot : spots) {
            if (spot.getSortOrder() >= index) {
                spotMapper.updateSortOrder(spot.getId(), spot.getSortOrder() + 1);
            }
        }
        ItinerarySpot spot = new ItinerarySpot();
        spot.setDayId(dayId);
        spot.setSortOrder(index);
        spot.setSource(SpotSource.MANUAL);
        spot.setName(request.getName());
        spot.setDescription(request.getDescription());
        spot.setSuggestedDuration(request.getSuggestedDuration());
        spotMapper.insert(spot);
        return new ItineraryDtos.AddSpotResponse(
                spot.getId(), dayId, index, SpotSource.MANUAL.name(),
                spotMapper.countManual(itineraryId));
    }

    @Transactional
    public ItineraryDtos.UpdateSpotResponse updateSpot(Long userId, Long spotId,
                                                       ItineraryDtos.UpdateSpotRequest request) {
        ItinerarySpot spot = requireSpot(userId, spotId);
        spot.setName(request.getName());
        spot.setDescription(request.getDescription());
        spot.setSuggestedDuration(request.getSuggestedDuration());
        spotMapper.updateById(spot);
        return new ItineraryDtos.UpdateSpotResponse(
                spotId, spot.getSource().name(), spot.getName());
    }

    @Transactional
    public ItineraryDtos.DeleteSpotResponse deleteSpot(Long userId, Long spotId) {
        ItinerarySpot spot = requireSpot(userId, spotId);
        Long dayId = spot.getDayId();
        ItineraryDay day = dayMapper.selectById(dayId);
        Long itineraryId = day == null ? null : day.getItineraryId();
        spotMapper.deleteSpotPhysically(spotId);
        compact(dayId);
        int manualCount = itineraryId == null ? 0 : spotMapper.countManual(itineraryId);
        return new ItineraryDtos.DeleteSpotResponse(spotId, manualCount);
    }

    @Transactional
    public ItineraryDtos.MoveSpotResponse moveSpot(Long userId, Long spotId,
                                                   ItineraryDtos.MoveSpotRequest request) {
        ItinerarySpot spot = requireSpot(userId, spotId);
        ItineraryDay targetDay = dayMapper.selectById(request.getTargetDayId());
        ItineraryDay sourceDay = dayMapper.selectById(spot.getDayId());
        if (targetDay == null || sourceDay == null
                || !targetDay.getItineraryId().equals(sourceDay.getItineraryId())) {
            throw new BusinessException(ErrorCode.SPOT_NOT_FOUND);
        }
        Long fromDayId = sourceDay.getId();
        Long toDayId = targetDay.getId();
        List<ItinerarySpot> source = spotMapper.selectByDay(fromDayId).stream()
                .filter(item -> !item.getId().equals(spotId)).toList();
        List<ItinerarySpot> target = spotMapper.selectByDay(toDayId).stream()
                .filter(item -> !item.getId().equals(spotId)).toList();
        int index = request.getTargetIndex() == null
                ? target.size() : Math.max(0, Math.min(target.size(), request.getTargetIndex()));
        for (int i = 0; i < source.size(); i++) {
            spotMapper.updateSortOrder(source.get(i).getId(), i);
        }
        List<ItinerarySpot> ordered = new ArrayList<>(target);
        ordered.add(index, spot);
        for (int i = 0; i < ordered.size(); i++) {
            spotMapper.moveSpot(ordered.get(i).getId(), toDayId, i);
        }
        return new ItineraryDtos.MoveSpotResponse(spotId, fromDayId, toDayId,
                List.of(affected(fromDayId), affected(toDayId)));
    }

    @Transactional
    public ItineraryDtos.Response addDay(Long userId, Long itineraryId, ItineraryDtos.AddDayRequest request) {
        Itinerary itinerary = requireOwned(userId, itineraryId);
        List<ItineraryDay> days = dayMapper.selectByItinerary(itineraryId);
        if (days.size() >= 15) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "单条行程最多 15 天");
        }
        int index = request.getInsertIndex();
        if (index < 1 || index > days.size() + 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "插入位置非法");
        }
        String cityCode = request.getCityCode().toUpperCase(Locale.ROOT);
        catalogService.requireEnabledCity(itinerary.getCountryCode(), cityCode);
        for (int i = days.size() - 1; i >= index - 1; i--) {
            ItineraryDay day = days.get(i);
            day.setDayIndex(day.getDayIndex() + 1);
            day.setVisitDate(day.getVisitDate().plusDays(1));
            dayMapper.updateById(day);
        }
        ItineraryDay day = new ItineraryDay();
        day.setItineraryId(itineraryId);
        day.setDayIndex(index);
        day.setVisitDate(request.getDate());
        day.setCityCode(cityCode);
        dayMapper.insert(day);
        recalculate(itinerary);
        return toResponse(requireOwned(userId, itineraryId));
    }

    @Transactional
    public ItineraryDtos.Response updateDay(Long userId, Long itineraryId, Long dayId,
                                            ItineraryDtos.UpdateDayRequest request) {
        Itinerary itinerary = requireOwned(userId, itineraryId);
        ItineraryDay day = requireDay(itineraryId, dayId);
        if (request.getDate() != null) {
            day.setVisitDate(request.getDate());
        }
        if (request.getCityCode() != null) {
            String cityCode = request.getCityCode().toUpperCase(Locale.ROOT);
            catalogService.requireEnabledCity(itinerary.getCountryCode(), cityCode);
            day.setCityCode(cityCode);
        }
        dayMapper.updateById(day);
        recalculate(itinerary);
        return toResponse(requireOwned(userId, itineraryId));
    }

    @Transactional
    public ItineraryDtos.DeleteDayResponse deleteDay(Long userId, Long itineraryId, Long dayId) {
        Itinerary itinerary = requireOwned(userId, itineraryId);
        List<ItineraryDay> days = dayMapper.selectByItinerary(itineraryId);
        if (days.size() <= 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "至少保留 1 天");
        }
        ItineraryDay day = requireDay(itineraryId, dayId);
        int deletedSpots = spotMapper.deleteByDayPhysically(dayId);
        dayMapper.deleteDayPhysically(dayId);
        for (ItineraryDay later : days) {
            if (later.getDayIndex() > day.getDayIndex()) {
                later.setDayIndex(later.getDayIndex() - 1);
                later.setVisitDate(later.getVisitDate().minusDays(1));
                dayMapper.updateById(later);
            }
        }
        recalculate(itinerary);
        Itinerary reloaded = requireOwned(userId, itineraryId);
        int dayCount = dayMapper.selectByItinerary(itineraryId).size();
        return new ItineraryDtos.DeleteDayResponse(
                itineraryId, deletedSpots, dayCount, reloaded.getStartDate(), reloaded.getEndDate());
    }

    private void insertDays(Long itineraryId, List<ItineraryDtos.DayRequest> days, String countryCode) {
        for (ItineraryDtos.DayRequest request : days) {
            ItineraryDay day = new ItineraryDay();
            day.setItineraryId(itineraryId);
            day.setDayIndex(request.getDayIndex());
            day.setVisitDate(request.getDate());
            day.setCityCode(request.getCityCode().toUpperCase(Locale.ROOT));
            dayMapper.insert(day);
            List<ItineraryDtos.SpotRequest> spots = request.getSpots() == null ? List.of() : request.getSpots();
            int order = 0;
            for (ItineraryDtos.SpotRequest spot : spots) {
                SpotSource source = "MANUAL".equalsIgnoreCase(spot.getSource())
                        ? SpotSource.MANUAL : SpotSource.AI;
                insertSpot(day.getId(), order++, source, spot.getName(),
                        spot.getDescription(), spot.getSuggestedDuration());
            }
        }
    }

    private void insertSpot(Long dayId, int order, SpotSource source, String name,
                            String description, String suggestedDuration) {
        ItinerarySpot spot = new ItinerarySpot();
        spot.setDayId(dayId);
        spot.setSortOrder(order);
        spot.setSource(source);
        spot.setName(name);
        spot.setDescription(description);
        spot.setSuggestedDuration(suggestedDuration);
        spotMapper.insert(spot);
    }

    private ItineraryDtos.Response toResponse(Itinerary itinerary) {
        List<ItineraryDay> days = dayMapper.selectByItinerary(itinerary.getId());
        List<ItineraryDtos.DayView> dayViews = new ArrayList<>();
        for (ItineraryDay day : days) {
            City city = catalogService.findCity(itinerary.getCountryCode(), day.getCityCode());
            boolean disabled = city == null || city.getStatus() != EnabledStatus.ENABLED;
            String cityName = city == null ? day.getCityCode() : city.getName();
            List<ItineraryDtos.SpotView> spots = spotMapper.selectByDay(day.getId()).stream()
                    .map(spot -> new ItineraryDtos.SpotView(
                            spot.getId(), spot.getSortOrder(), spot.getSource().name(),
                            spot.getName(), spot.getDescription(), spot.getSuggestedDuration()))
                    .toList();
            dayViews.add(new ItineraryDtos.DayView(
                    day.getId(), day.getDayIndex(), day.getVisitDate(),
                    day.getCityCode(), cityName, disabled, spots));
        }
        Trip trip = itinerary.getTripId() == null ? null
                : tripMapper.selectById(itinerary.getTripId());
        return new ItineraryDtos.Response(
                itinerary.getId(),
                itinerary.getTripId(),
                trip == null ? null : trip.getName(),
                itinerary.getCountryCode(),
                catalogService.countryName(itinerary.getCountryCode()),
                itinerary.getTitle(),
                itinerary.getStartDate(),
                itinerary.getEndDate(),
                itinerary.getStatus().name(),
                itinerary.getEndDate().isBefore(LocalDate.now()),
                parseGuides(itinerary.getGuidesJson()),
                parsePreparations(itinerary.getPreparationsJson()),
                dayViews,
                spotMapper.countManual(itinerary.getId()),
                itinerary.getCreatedAt(),
                itinerary.getUpdatedAt());
    }

    private Itinerary requireOwned(Long userId, Long id) {
        Itinerary itinerary = itineraryMapper.selectById(id);
        if (itinerary == null || !userId.equals(itinerary.getUserId())) {
            throw new BusinessException(ErrorCode.ITINERARY_NOT_FOUND);
        }
        return itinerary;
    }

    private ItineraryDay requireDay(Long itineraryId, Long dayId) {
        ItineraryDay day = dayMapper.selectById(dayId);
        if (day == null || !itineraryId.equals(day.getItineraryId())) {
            throw new BusinessException(ErrorCode.SPOT_NOT_FOUND, "天不存在或不属于该行程");
        }
        return day;
    }

    private ItinerarySpot requireSpot(Long userId, Long spotId) {
        ItinerarySpot spot = spotMapper.selectById(spotId);
        if (spot == null) {
            throw new BusinessException(ErrorCode.SPOT_NOT_FOUND);
        }
        ItineraryDay day = dayMapper.selectById(spot.getDayId());
        if (day == null) {
            throw new BusinessException(ErrorCode.SPOT_NOT_FOUND);
        }
        requireOwned(userId, day.getItineraryId());
        return spot;
    }

    private void recalculate(Itinerary itinerary) {
        itineraryMapper.recalculateDates(itinerary.getId());
        if (itinerary.getTripId() != null) {
            tripService.recalculateDates(itinerary.getTripId());
        }
    }

    private void compact(Long dayId) {
        List<ItinerarySpot> spots = spotMapper.selectByDay(dayId);
        for (int i = 0; i < spots.size(); i++) {
            spotMapper.updateSortOrder(spots.get(i).getId(), i);
        }
    }

    private ItineraryDtos.AffectedDay affected(Long dayId) {
        return new ItineraryDtos.AffectedDay(dayId,
                spotMapper.selectByDay(dayId).stream().map(ItinerarySpot::getId).toList());
    }

    private List<GuideLink> parseGuides(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<GuideLink>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private GenerateItineraryResponse.Preparations parsePreparations(String json) {
        if (json == null || json.isBlank()) {
            return emptyPreparations();
        }
        try {
            return objectMapper.readValue(json, GenerateItineraryResponse.Preparations.class);
        } catch (Exception ex) {
            return emptyPreparations();
        }
    }

    private GenerateItineraryResponse.Preparations emptyPreparations() {
        return new GenerateItineraryResponse.Preparations("", "", "", "", List.of());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
