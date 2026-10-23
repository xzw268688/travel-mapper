package com.travelmapper.controller.admin.dto;

import java.util.List;

public record GenerateCardsResponse(String batchId, int quantity, List<String> codes) {
}
