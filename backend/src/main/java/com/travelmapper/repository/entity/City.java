package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.CityLevel;
import com.travelmapper.repository.enums.EnabledStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("city")
public class City {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String countryCode;
    private String cityCode;
    private String name;
    private String nameEn;
    private CityLevel level;
    private EnabledStatus status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
