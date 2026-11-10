package com.travelmapper.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmapper.repository.entity.ItinerarySpot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ItinerarySpotMapper extends BaseMapper<ItinerarySpot> {

    @Delete("DELETE FROM itinerary_spot WHERE day_id IN "
            + "(SELECT id FROM itinerary_day WHERE itinerary_id = #{itineraryId})")
    int deleteByItineraryIdPhysically(Long itineraryId);

    @Select("SELECT * FROM itinerary_spot WHERE day_id = #{dayId} ORDER BY sort_order ASC, id ASC")
    List<ItinerarySpot> selectByDay(Long dayId);

    @Select("SELECT COUNT(*) FROM itinerary_spot s "
            + "JOIN itinerary_day d ON s.day_id = d.id "
            + "WHERE d.itinerary_id = #{itineraryId} AND s.source = 'MANUAL'")
    int countManual(Long itineraryId);

    @Select("SELECT s.* FROM itinerary_spot s "
            + "JOIN itinerary_day d ON s.day_id = d.id "
            + "WHERE d.itinerary_id = #{itineraryId} AND s.source = 'MANUAL' "
            + "ORDER BY d.day_index ASC, s.sort_order ASC")
    List<ItinerarySpot> selectManual(Long itineraryId);

    @Update("UPDATE itinerary_spot SET day_id = #{dayId}, sort_order = #{sortOrder} WHERE id = #{id}")
    int moveSpot(@Param("id") Long id, @Param("dayId") Long dayId, @Param("sortOrder") int sortOrder);

    @Update("UPDATE itinerary_spot SET sort_order = #{sortOrder} WHERE id = #{id}")
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") int sortOrder);

    @Delete("DELETE FROM itinerary_spot WHERE day_id = #{dayId}")
    int deleteByDayPhysically(Long dayId);

    @Delete("DELETE FROM itinerary_spot WHERE id = #{id}")
    int deleteSpotPhysically(Long id);
}
