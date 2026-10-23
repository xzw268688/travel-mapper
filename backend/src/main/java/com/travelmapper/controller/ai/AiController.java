package com.travelmapper.controller.ai;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.ai.dto.PolishRequest;
import com.travelmapper.service.ai.AiCancelService;
import com.travelmapper.service.ai.AiPolishService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Tag(name = "AI")
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiPolishService polishService;
    private final AiCancelService cancelService;

    public AiController(AiPolishService polishService, AiCancelService cancelService) {
        this.polishService = polishService;
        this.cancelService = cancelService;
    }

    @PostMapping(value = "/polish/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter polish(@Valid @RequestBody PolishRequest request) {
        return polishService.stream(CurrentUser.id(), request);
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestBody Map<String, String> body) {
        cancelService.cancel(CurrentUser.id(), body.get("requestId"));
        return ApiResponse.ok();
    }
}
