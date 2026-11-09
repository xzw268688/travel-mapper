package com.travelmapper.controller.announcement.dto;

import com.travelmapper.repository.enums.AnnouncementType;

import java.time.LocalDateTime;
import java.util.List;

public final class AnnouncementUserDtos {

    private AnnouncementUserDtos() {
    }

    public record AnnouncementView(
            Long id,
            String title,
            String content,
            AnnouncementType type,
            boolean pinned,
            LocalDateTime publishAt,
            LocalDateTime expireAt,
            boolean read) {
    }

    public record ActiveAnnouncementsResponse(long total, long unread, List<AnnouncementView> list) {
    }
}