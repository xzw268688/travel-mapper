package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.QuotaSource;
import com.travelmapper.repository.enums.QuotaType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quota_ledger")
public class QuotaLedger {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private QuotaType type;
    private Integer delta;
    private QuotaSource source;
    private String refId;
    private String remark;
    private LocalDateTime createdAt;
}
