package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.ReportReason;
import com.travelmapper.repository.enums.ReportStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_report")
public class NoteReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long noteId;
    private Long reporterUserId;
    private ReportReason reason;
    private String description;
    private String noteTitle;
    private String noteContentText;
    private LocalDateTime noteUpdatedAt;
    private ReportStatus status;
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}