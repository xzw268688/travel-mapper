package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.Role;
import com.travelmapper.repository.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatarPreset;
    private Role role;
    private UserStatus status;
    private Boolean emailVerified;
    private LocalDateTime vipExpireAt;
    private Integer tokenVersion;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
