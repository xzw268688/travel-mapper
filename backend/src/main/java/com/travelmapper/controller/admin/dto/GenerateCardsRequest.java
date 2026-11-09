package com.travelmapper.controller.admin.dto;

import com.travelmapper.repository.enums.CardType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GenerateCardsRequest {
    private CardType type;
    private Integer value;
    private BigDecimal priceCny;
    private LocalDateTime expireAt;
    private Integer quantity;
    private String prefix;
}
