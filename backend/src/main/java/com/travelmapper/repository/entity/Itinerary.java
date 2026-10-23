package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.ItineraryStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("itinerary")
public class Itinerary {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tripId;
    private String countryCode;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String guidesJson;
    private String preparationsJson;
    private ItineraryStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
