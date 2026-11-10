package com.travelmapper.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmapper.repository.entity.ItineraryDay;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ItineraryDayMapper extends BaseMapper<ItineraryDay> {

    @Delete("DELETE FROM itinerary_day WHERE itinerary_id = #{itineraryId}")
    int deleteByItineraryIdPhysically(Long itineraryId);

    @Select("SELECT * FROM itinerary_day WHERE itinerary_id = #{itineraryId} ORDER BY day_index ASC")
    List<ItineraryDay> selectByItinerary(Long itineraryId);

    @Select("SELECT * FROM itinerary_day WHERE itinerary_id = #{itineraryId} ORDER BY day_index DESC")
    List<ItineraryDay> selectByItineraryDesc(Long itineraryId);

    @Update("UPDATE itinerary_day SET day_index = #{dayIndex} WHERE id = #{id}")
    int updateDayIndex(@Param("id") Long id, @Param("dayIndex") int dayIndex);

    @Delete("DELETE FROM itinerary_day WHERE id = #{id}")
    int deleteDayPhysically(Long id);
}
