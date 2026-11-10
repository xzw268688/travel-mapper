package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.controller.admin.dto.CardBatchStatsView;
import com.travelmapper.controller.admin.dto.CardKeyView;
import com.travelmapper.controller.admin.dto.CardRedemptionView;
import com.travelmapper.controller.admin.dto.GenerateCardsRequest;
import com.travelmapper.controller.admin.dto.GenerateCardsResponse;
import com.travelmapper.controller.admin.dto.VoidCardResponse;
import com.travelmapper.repository.enums.CardStatus;
import com.travelmapper.service.card.CardKeyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Tag(name = "Admin Cards")
@RestController
@RequestMapping("/api/admin/cards")
public class AdminCardController {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CardKeyService cardKeyService;
    private final TimeProvider timeProvider;

    public AdminCardController(CardKeyService cardKeyService, TimeProvider timeProvider) {
        this.cardKeyService = cardKeyService;
        this.timeProvider = timeProvider;
    }

    @PostMapping("/generate")
    public ApiResponse<GenerateCardsResponse> generate(@RequestBody GenerateCardsRequest request) {
        return ApiResponse.ok(cardKeyService.generate(CurrentUser.id(), request));
    }

    @GetMapping
    public ApiResponse<PageResult<CardKeyView>> list(
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) CardStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = new PageRequest();
        request.setPage(page);
        request.setSize(size);
        return ApiResponse.ok(cardKeyService.list(batchId, status, request));
    }

    @GetMapping("/stats")
    public ApiResponse<List<CardBatchStatsView>> stats() {
        return ApiResponse.ok(cardKeyService.batchStats());
    }

    @GetMapping("/redemptions")
    public ApiResponse<PageResult<CardRedemptionView>> redemptions(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = new PageRequest();
        request.setPage(page);
        request.setSize(size);
        return ApiResponse.ok(cardKeyService.redemptions(userId, request));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String batchId) {
        byte[] body = cardKeyService.exportCsv(batchId);
        String filename = "cards_" + batchId + "_" + timeProvider.now().format(FILE_TIME) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=utf-8"))
                .body(body);
    }

    @PutMapping("/{id}/void")
    public ApiResponse<VoidCardResponse> voidCard(@PathVariable Long id) {
        return ApiResponse.ok(cardKeyService.voidCard(CurrentUser.id(), id));
    }
}
