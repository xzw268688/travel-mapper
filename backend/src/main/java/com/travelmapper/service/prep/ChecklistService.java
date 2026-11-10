package com.travelmapper.service.prep;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.controller.prep.dto.ChecklistDtos;
import com.travelmapper.repository.entity.Itinerary;
import com.travelmapper.repository.entity.TripChecklist;
import com.travelmapper.repository.mapper.ItineraryMapper;
import com.travelmapper.repository.mapper.TripChecklistMapper;
import com.travelmapper.service.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ChecklistService {

    private static final int CUSTOM_SORT_START = 110;

    private final TripChecklistMapper checklistMapper;
    private final ItineraryMapper itineraryMapper;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;

    public ChecklistService(TripChecklistMapper checklistMapper, ItineraryMapper itineraryMapper,
                            AuditLogService auditLogService, TimeProvider timeProvider) {
        this.checklistMapper = checklistMapper;
        this.itineraryMapper = itineraryMapper;
        this.auditLogService = auditLogService;
        this.timeProvider = timeProvider;
    }

    /** 新建规划使用的默认模板，不依赖行程。 */
    public ChecklistDtos.ChecklistResponse template() {
        List<ChecklistDtos.Item> items = PrepChecklistTemplate.ITEMS.stream()
                .map(item -> new ChecklistDtos.Item(
                        null, item.key(), item.title(), item.title(), item.description(),
                        item.daysBefore(), false, false, item.sortOrder()))
                .toList();
        return new ChecklistDtos.ChecklistResponse(null, null, items);
    }

    public ChecklistDtos.ChecklistResponse get(Long userId, Long itineraryId) {
        Itinerary itinerary = requireOwned(userId, itineraryId);
        List<TripChecklist> rows = rows(userId, itineraryId);
        Map<String, TripChecklist> byKey = new HashMap<>();
        for (TripChecklist row : rows) {
            byKey.put(row.getItemKey(), row);
        }

        List<ChecklistDtos.Item> items = new ArrayList<>();
        for (PrepChecklistTemplate.DefaultItem template : PrepChecklistTemplate.ITEMS) {
            TripChecklist row = byKey.get(template.key());
            items.add(new ChecklistDtos.Item(
                    row == null ? null : row.getId(),
                    template.key(),
                    template.title(),
                    template.title(),
                    template.description(),
                    template.daysBefore(),
                    row != null && Boolean.TRUE.equals(row.getChecked()),
                    false,
                    template.sortOrder()));
        }

        rows.stream()
                .filter(row -> !PrepChecklistTemplate.isDefault(row.getItemKey()))
                .sorted(Comparator
                        .comparingInt((TripChecklist row) -> row.getSortOrder() == null ? 0 : row.getSortOrder())
                        .thenComparing(TripChecklist::getId))
                .forEach(row -> items.add(toCustomItem(row)));

        return new ChecklistDtos.ChecklistResponse(
                itinerary.getId(), itinerary.getCountryCode(), items);
    }

    @Transactional
    public ChecklistDtos.UpdateResponse upsert(Long userId, ChecklistDtos.UpdateRequest request) {
        Itinerary itinerary = requireOwned(userId, request.getItineraryId());
        String key = request.getItemKey().trim().toUpperCase(Locale.ROOT);
        boolean checked = Boolean.TRUE.equals(request.getChecked());
        var template = PrepChecklistTemplate.find(key);
        if (template.isPresent()) {
            PrepChecklistTemplate.DefaultItem item = template.get();
            checklistMapper.upsert(userId, itinerary.getCountryCode(), itinerary.getId(),
                    item.key(), item.title(), item.description(), item.daysBefore(),
                    item.sortOrder(), false, checked, timeProvider.now());
        } else {
            TripChecklist row = findByKey(userId, itinerary.getId(), request.getItemKey().trim());
            if (row == null) {
                throw new BusinessException(ErrorCode.CHECKLIST_ITEM_INVALID);
            }
            row.setChecked(checked);
            checklistMapper.updateById(row);
        }
        return new ChecklistDtos.UpdateResponse(request.getItemKey(), checked, timeProvider.now());
    }

    @Transactional
    public ChecklistDtos.Item addCustom(Long userId, ChecklistDtos.AddItemRequest request) {
        Itinerary itinerary = requireOwned(userId, request.getItineraryId());
        String title = validateTitle(request.getTitle());
        String description = validateDescription(request.getDescription());
        int days = validateDays(request.getDaysBefore());
        List<TripChecklist> rows = rows(userId, itinerary.getId());
        int sortOrder = nextSortOrder(rows);

        TripChecklist row = new TripChecklist();
        row.setUserId(userId);
        row.setCountryCode(itinerary.getCountryCode());
        row.setItineraryId(itinerary.getId());
        row.setItemKey("CUSTOM_" + UUID.randomUUID().toString().replace("-", ""));
        row.setTitle(title);
        row.setDescription(description);
        row.setDaysBefore(days);
        row.setSortOrder(sortOrder);
        row.setCustom(true);
        row.setChecked(false);
        row.setDeleted(0);
        checklistMapper.insert(row);
        return toCustomItem(row);
    }

    @Transactional
    public ChecklistDtos.DeleteResponse deleteCustom(Long userId, Long itemId) {
        TripChecklist row = checklistMapper.selectById(itemId);
        if (row == null || !userId.equals(row.getUserId())) {
            throw new BusinessException(ErrorCode.ITINERARY_NOT_FOUND);
        }
        requireOwned(userId, row.getItineraryId());
        if (!Boolean.TRUE.equals(row.getCustom())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "默认清单项不能删除");
        }
        checklistMapper.deleteById(itemId);
        return new ChecklistDtos.DeleteResponse(itemId);
    }

    /** 新建行程保存后，用完整清单覆盖该行程的清单状态。 */
    @Transactional
    public ChecklistDtos.ChecklistResponse batchSave(Long userId, ChecklistDtos.BatchRequest request) {
        Itinerary itinerary = requireOwned(userId, request.getItineraryId());
        List<TripChecklist> existing = rows(userId, itinerary.getId());
        int nextSort = nextSortOrder(existing);
        Set<String> submittedCustomKeys = new HashSet<>();

        for (ChecklistDtos.BatchItem item : request.getItems()) {
            String rawKey = item.getItemKey() == null ? "" : item.getItemKey().trim();
            var template = PrepChecklistTemplate.find(rawKey.toUpperCase(Locale.ROOT));
            if (template.isPresent()) {
                PrepChecklistTemplate.DefaultItem def = template.get();
                checklistMapper.upsert(userId, itinerary.getCountryCode(), itinerary.getId(),
                        def.key(), def.title(), def.description(), def.daysBefore(),
                        def.sortOrder(), false, Boolean.TRUE.equals(item.getChecked()),
                        timeProvider.now());
                continue;
            }

            String key = rawKey;
            if (key.isBlank()) {
                key = "CUSTOM_" + UUID.randomUUID().toString().replace("-", "");
            } else if (!key.startsWith("CUSTOM_")) {
                throw new BusinessException(ErrorCode.CHECKLIST_ITEM_INVALID);
            }
            final String customKey = key;
            submittedCustomKeys.add(customKey);

            String title = validateTitle(item.getTitle());
            String description = validateDescription(item.getDescription());
            int days = validateDays(item.getDaysBefore());
            TripChecklist current = existing.stream()
                    .filter(row -> customKey.equals(row.getItemKey()))
                    .findFirst()
                    .orElse(null);
            int sortOrder = current != null && current.getSortOrder() != null
                    ? current.getSortOrder() : nextSort++;
            checklistMapper.upsert(userId, itinerary.getCountryCode(), itinerary.getId(),
                    customKey, title, description, days, sortOrder, true,
                    Boolean.TRUE.equals(item.getChecked()), timeProvider.now());
        }

        // 本次没有提交的自定义项视为已删除。
        existing.stream()
                .filter(row -> !PrepChecklistTemplate.isDefault(row.getItemKey()))
                .filter(row -> !submittedCustomKeys.contains(row.getItemKey()))
                .forEach(row -> checklistMapper.deleteById(row.getId()));

        long checkedCount = request.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .count();
        auditLogService.record(userId, "CHECKLIST_SAVE", "ITINERARY", itinerary.getId(),
                Map.of("itemCount", request.getItems().size(), "checkedCount", checkedCount), null);
        return get(userId, itinerary.getId());
    }

    private List<TripChecklist> rows(Long userId, Long itineraryId) {
        return checklistMapper.selectList(new LambdaQueryWrapper<TripChecklist>()
                .eq(TripChecklist::getUserId, userId)
                .eq(TripChecklist::getItineraryId, itineraryId));
    }

    private TripChecklist findByKey(Long userId, Long itineraryId, String itemKey) {
        List<TripChecklist> rows = checklistMapper.selectList(new LambdaQueryWrapper<TripChecklist>()
                .eq(TripChecklist::getUserId, userId)
                .eq(TripChecklist::getItineraryId, itineraryId)
                .eq(TripChecklist::getItemKey, itemKey)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ChecklistDtos.Item toCustomItem(TripChecklist row) {
        return new ChecklistDtos.Item(
                row.getId(),
                row.getItemKey(),
                row.getTitle() == null ? row.getItemKey() : row.getTitle(),
                row.getTitle() == null ? row.getItemKey() : row.getTitle(),
                row.getDescription(),
                row.getDaysBefore() == null ? 0 : row.getDaysBefore(),
                Boolean.TRUE.equals(row.getChecked()),
                true,
                row.getSortOrder() == null ? CUSTOM_SORT_START : row.getSortOrder());
    }

    private int nextSortOrder(List<TripChecklist> rows) {
        return rows.stream()
                .map(TripChecklist::getSortOrder)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .map(value -> value + 10)
                .orElse(CUSTOM_SORT_START);
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank() || title.length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "清单项标题必须为 1-64 字");
        }
        return title.trim();
    }

    private String validateDescription(String description) {
        if (description != null && description.length() > 255) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "清单项说明不能超过 255 字");
        }
        return description == null || description.isBlank() ? null : description.trim();
    }

    private int validateDays(Integer days) {
        if (days == null || days < 0 || days > 365) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "提前天数必须为 0-365");
        }
        return days;
    }

    private Itinerary requireOwned(Long userId, Long itineraryId) {
        Itinerary itinerary = itineraryMapper.selectById(itineraryId);
        if (itinerary == null || !userId.equals(itinerary.getUserId())) {
            throw new BusinessException(ErrorCode.ITINERARY_NOT_FOUND);
        }
        return itinerary;
    }
}