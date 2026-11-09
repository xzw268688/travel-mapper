package com.travelmapper.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmapper.repository.entity.TripChecklist;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface TripChecklistMapper extends BaseMapper<TripChecklist> {

    @Insert("INSERT INTO trip_checklist "
            + "(user_id, country_code, itinerary_id, item_key, title, description, days_before, "
            + " sort_order, custom, checked, deleted) "
            + "VALUES (#{userId}, #{countryCode}, #{itineraryId}, #{itemKey}, #{title}, #{description}, "
            + " #{daysBefore}, #{sortOrder}, #{custom}, #{checked}, 0) "
            + "ON DUPLICATE KEY UPDATE title = VALUES(title), description = VALUES(description), "
            + " days_before = VALUES(days_before), sort_order = VALUES(sort_order), "
            + " custom = VALUES(custom), checked = VALUES(checked), deleted = 0, updated_at = NOW()")
    int upsert(@Param("userId") Long userId, @Param("countryCode") String countryCode,
               @Param("itineraryId") Long itineraryId, @Param("itemKey") String itemKey,
               @Param("title") String title, @Param("description") String description,
               @Param("daysBefore") Integer daysBefore, @Param("sortOrder") int sortOrder,
               @Param("custom") boolean custom, @Param("checked") boolean checked);
}