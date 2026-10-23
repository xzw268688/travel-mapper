package com.travelmapper.controller.admin.dto;

import com.travelmapper.repository.enums.CardType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GenerateCardsRequest {
    private CardType type;
    private Integer value;
    private LocalDateTime expireAt;
    private Integer quantity;
    private String prefix;
}
