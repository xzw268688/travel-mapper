package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.EmailTokenType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_token")
public class EmailToken {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String email;
    private String token;
    private EmailTokenType type;
    private LocalDateTime expireAt;
    private Boolean used;
    private LocalDateTime createdAt;
}
