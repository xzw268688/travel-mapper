package com.travelmapper.service.prep;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.controller.prep.dto.ChecklistDtos;
import com.travelmapper.repository.entity.Itinerary;
import com.travelmapper.repository.entity.TripChecklist;
import com.travelmapper.repository.enums.ChecklistItem;
import com.travelmapper.repository.mapper.ItineraryMapper;
import com.travelmapper.repository.mapper.TripChecklistMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ChecklistService {

    private final TripChecklistMapper checklistMapper;
    private final ItineraryMapper itineraryMapper;

    public ChecklistService(TripChecklistMapper checklistMapper, ItineraryMapper itineraryMapper) {
        this.checklistMapper = checklistMapper;
        this.itineraryMapper = itineraryMapper;
    }

    public ChecklistDtos.ChecklistResponse get(Long userId, Long itineraryId) {
        Itinerary itinerary = requireOwned(userId, itineraryId);
        List<TripChecklist> rows = checklistMapper.selectList(
                new LambdaQueryWrapper<TripChecklist>()
                        .eq(TripChecklist::getUserId, userId)
                        .eq(TripChecklist::getItineraryId, itineraryId));
        Map<ChecklistItem, Boolean> checked = new HashMap<>();
        for (TripChecklist row : rows) {
            checked.put(row.getItemKey(), Boolean.TRUE.equals(row.getChecked()));
        }
        List<ChecklistDtos.Item> items = List.of(ChecklistItem.values()).stream()
                .map(item -> new ChecklistDtos.Item(
                        item.name(), item.getLabel(),
                        checked.getOrDefault(item, false)))
                .toList();
        return new ChecklistDtos.ChecklistResponse(
                itineraryId, itinerary.getCountryCode(), items);
    }

    @Transactional
    public ChecklistDtos.UpdateResponse upsert(Long userId, ChecklistDtos.UpdateRequest request) {
        Itinerary itinerary = requireOwned(userId, request.getItineraryId());
        ChecklistItem item;
        try {
            item = ChecklistItem.valueOf(request.getItemKey().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.CHECKLIST_ITEM_INVALID);
        }
        checklistMapper.upsert(userId, itinerary.getCountryCode(), itinerary.getId(),
                item.name(), Boolean.TRUE.equals(request.getChecked()));
        return new ChecklistDtos.UpdateResponse(
                item.name(), Boolean.TRUE.equals(request.getChecked()), LocalDateTime.now());
    }

    private Itinerary requireOwned(Long userId, Long itineraryId) {
        Itinerary itinerary = itineraryMapper.selectById(itineraryId);
        if (itinerary == null || !userId.equals(itinerary.getUserId())) {
            throw new BusinessException(ErrorCode.ITINERARY_NOT_FOUND);
        }
        return itinerary;
    }
}
