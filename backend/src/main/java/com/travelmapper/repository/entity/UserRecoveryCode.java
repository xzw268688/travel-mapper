package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_recovery_code")
public class UserRecoveryCode {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String codeHash;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}