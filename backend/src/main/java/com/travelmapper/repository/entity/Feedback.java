package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.FeedbackStatus;
import com.travelmapper.repository.enums.FeedbackType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feedback")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private FeedbackType type;
    private String title;
    private String content;
    private String contact;
    private String pagePath;
    private String userAgent;
    private String appVersion;
    private FeedbackStatus status;
    private String adminNote;
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}