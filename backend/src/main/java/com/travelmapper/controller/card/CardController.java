package com.travelmapper.controller.card;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.card.dto.CardRedeemResponse;
import com.travelmapper.controller.card.dto.RedeemCardRequest;
import com.travelmapper.service.card.CardKeyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Card")
@RestController
@RequestMapping("/api/card")
public class CardController {

    private final CardKeyService cardKeyService;

    public CardController(CardKeyService cardKeyService) {
        this.cardKeyService = cardKeyService;
    }

    @PostMapping("/redeem")
    public ApiResponse<CardRedeemResponse> redeem(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RedeemCardRequest request) {
        return ApiResponse.ok(cardKeyService.redeem(
                CurrentUser.id(), request.getCode(), idempotencyKey));
    }
}
