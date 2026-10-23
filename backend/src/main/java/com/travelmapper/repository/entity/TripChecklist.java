package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.ChecklistItem;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trip_checklist")
public class TripChecklist {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String countryCode;
    private Long itineraryId;
    private ChecklistItem itemKey;
    private Boolean checked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
