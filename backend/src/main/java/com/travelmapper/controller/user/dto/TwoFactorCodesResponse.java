package com.travelmapper.controller.user.dto;

import java.util.List;

public record TwoFactorCodesResponse(List<String> recoveryCodes) {
}