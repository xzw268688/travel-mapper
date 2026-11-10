package com.travelmapper.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmapper.repository.entity.Itinerary;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ItineraryMapper extends BaseMapper<Itinerary> {

    @Select("SELECT * FROM itinerary WHERE id = #{id}")
    Itinerary selectByIdIncludingDeleted(Long id);

    @Update("UPDATE itinerary i SET "
            + "i.start_date = (SELECT MIN(d.visit_date) FROM itinerary_day d WHERE d.itinerary_id = i.id), "
            + "i.end_date = (SELECT MAX(d.visit_date) FROM itinerary_day d WHERE d.itinerary_id = i.id) "
            + "WHERE i.id = #{id}")
    int recalculateDates(@Param("id") Long id);
}
