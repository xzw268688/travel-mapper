package com.travelmapper.controller.card.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RedeemCardRequest {

    @NotBlank
    private String code;
}
