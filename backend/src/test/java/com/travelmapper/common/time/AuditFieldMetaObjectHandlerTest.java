package com.travelmapper.common.time;

import com.travelmapper.repository.entity.TravelNote;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuditFieldMetaObjectHandlerTest {

    private final TimeProvider timeProvider = new TimeProvider(
            Clock.fixed(Instant.parse("2026-11-09T16:30:00Z"), TimeProvider.BEIJING));
    private final AuditFieldMetaObjectHandler handler = new AuditFieldMetaObjectHandler(timeProvider);

    @Test
    void fillsCreatedAtAndUpdatedAtOnInsert() {
        TravelNote note = new TravelNote();
        var metaObject = SystemMetaObject.forObject(note);

        handler.insertFill(metaObject);

        LocalDateTime expected = LocalDateTime.of(2026, 11, 10, 0, 30);
        assertThat(note.getCreatedAt()).isEqualTo(expected);
        assertThat(note.getUpdatedAt()).isEqualTo(expected);
    }

    @Test
    void keepsExplicitTimestampsOnInsert() {
        LocalDateTime explicit = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        TravelNote note = new TravelNote();
        note.setCreatedAt(explicit);
        note.setUpdatedAt(explicit);

        handler.insertFill(SystemMetaObject.forObject(note));

        assertThat(note.getCreatedAt()).isEqualTo(explicit);
        assertThat(note.getUpdatedAt()).isEqualTo(explicit);
    }

    @Test
    void refreshesUpdatedAtOnUpdateWithoutTouchingCreatedAt() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        TravelNote note = new TravelNote();
        note.setCreatedAt(created);
        note.setUpdatedAt(created);

        handler.updateFill(SystemMetaObject.forObject(note));

        assertThat(note.getCreatedAt()).isEqualTo(created);
        assertThat(note.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 0, 30));
    }

    @Test
    void ignoresObjectsWithoutTimestampFields() {
        Object value = new Object() {
            @SuppressWarnings("unused")
            private String name;
        };

        handler.insertFill(SystemMetaObject.forObject(value));
        handler.updateFill(SystemMetaObject.forObject(value));
    }
}