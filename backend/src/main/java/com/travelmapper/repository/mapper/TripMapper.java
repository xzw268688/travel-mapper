package com.travelmapper.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmapper.repository.entity.Trip;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TripMapper extends BaseMapper<Trip> {

    @Update("UPDATE trip t SET "
            + "t.start_date = (SELECT MIN(i.start_date) FROM itinerary i "
            + "  WHERE i.trip_id = t.id AND i.deleted = 0), "
            + "t.end_date = (SELECT MAX(i.end_date) FROM itinerary i "
            + "  WHERE i.trip_id = t.id AND i.deleted = 0) "
            + "WHERE t.id = #{tripId}")
    int recalculateDates(@Param("tripId") Long tripId);
}
