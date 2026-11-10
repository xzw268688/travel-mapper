package com.travelmapper.controller.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminVipRequest {
    /** 为空表示清除 VIP；过去时间表示立即收回。 */
    private LocalDateTime expireAt;
}
