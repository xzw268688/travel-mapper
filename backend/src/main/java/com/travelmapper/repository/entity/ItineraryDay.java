package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("itinerary_day")
public class ItineraryDay {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long itineraryId;
    private Integer dayIndex;
    private LocalDate visitDate;
    private String cityCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
