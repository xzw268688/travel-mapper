package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quota_pack")
public class QuotaPack {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long cardKeyId;
    private Integer grantedCount;
    private Integer usedCount;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
