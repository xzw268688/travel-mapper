package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.Continent;
import com.travelmapper.repository.enums.EnabledStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("country")
public class Country {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String nameZh;
    private String nameEn;
    private Continent continent;
    private EnabledStatus status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
