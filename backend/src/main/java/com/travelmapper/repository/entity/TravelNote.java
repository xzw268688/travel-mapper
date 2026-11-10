package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.Visibility;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("travel_note")
public class TravelNote {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tripId;
    private String countryCode;
    private String cityCode;
    private String title;
    private String content;
    private String contentText;
    private String imagesJson;
    private String videosJson;
    private String coverImage;
    private Visibility visibility;
    private Boolean takedown;
    private String takedownReason;
    private LocalDateTime takedownAt;
    private Long takedownBy;
    private LocalDateTime mediaReviewedAt;
    private Long mediaReviewedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
