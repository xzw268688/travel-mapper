package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_share")
public class NoteShare {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long noteId;
    private String token;
    private LocalDateTime expireAt;
    private Boolean revoked;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
}
