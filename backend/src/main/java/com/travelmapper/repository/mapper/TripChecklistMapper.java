package com.travelmapper.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmapper.repository.entity.TripChecklist;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface TripChecklistMapper extends BaseMapper<TripChecklist> {

    @Insert("INSERT INTO trip_checklist "
            + "(user_id, country_code, itinerary_id, item_key, checked, deleted) "
            + "VALUES (#{userId}, #{countryCode}, #{itineraryId}, #{itemKey}, #{checked}, 0) "
            + "ON DUPLICATE KEY UPDATE checked = VALUES(checked), deleted = 0, updated_at = NOW()")
    int upsert(@Param("userId") Long userId, @Param("countryCode") String countryCode,
               @Param("itineraryId") Long itineraryId, @Param("itemKey") String itemKey,
               @Param("checked") boolean checked);
}
