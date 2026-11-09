package com.travelmapper.controller.admin.dto;

import com.travelmapper.repository.enums.FeedbackStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFeedbackRequest {

    private FeedbackStatus status;

    @Size(max = 1000)
    private String adminNote;
}