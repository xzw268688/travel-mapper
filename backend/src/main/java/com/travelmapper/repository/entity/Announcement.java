package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.AnnouncementStatus;
import com.travelmapper.repository.enums.AnnouncementType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("announcement")
public class Announcement {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private AnnouncementType type;
    private Boolean pinned;
    private AnnouncementStatus status;
    /** epoch 毫秒，避免数据库时区与 JVM 时区不一致导致生效窗口偏移。 */
    private Long publishAt;
    private Long expireAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}