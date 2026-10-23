package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.SpotSource;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("itinerary_spot")
public class ItinerarySpot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dayId;
    private Integer sortOrder;
    private SpotSource source;
    private String name;
    private String description;
    private String suggestedDuration;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
