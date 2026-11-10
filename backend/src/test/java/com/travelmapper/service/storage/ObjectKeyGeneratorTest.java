package com.travelmapper.service.storage;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.time.TimeProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectKeyGeneratorTest {

    private final TimeProvider timeProvider = new TimeProvider(
            Clock.fixed(Instant.parse("2026-11-30T15:30:00Z"), TimeProvider.BEIJING));
    private final ObjectKeyGenerator generator = new ObjectKeyGenerator(timeProvider);

    @Test
    void generatesNoteKeyWithUuidAndWhitelistedExtension() {
        String key = generator.generate(12L, "image/jpeg");
        assertThat(key).matches("note/12/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.jpg");
        assertThat(key).doesNotContain("original");
    }

    @Test
    void mapsPngAndWebp() {
        assertThat(generator.generate(1L, "image/png")).endsWith(".png");
        assertThat(generator.generate(1L, "image/webp")).endsWith(".webp");
    }

    @Test
    void mapsQuickTimeToMov() {
        assertThat(generator.generate(1L, "video/quicktime")).endsWith(".mov");
    }

    @Test
    void usesBeijingMonthAcrossUtcMonthBoundary() {
        // UTC 2026-11-30 16:30 = 北京 2026-12-01 00:30
        TimeProvider december = new TimeProvider(
                Clock.fixed(Instant.parse("2026-11-30T16:30:00Z"), TimeProvider.BEIJING));
        ObjectKeyGenerator decemberGenerator = new ObjectKeyGenerator(december);

        assertThat(decemberGenerator.generate(1L, "image/jpeg")).contains("/2026/12/");
        // UTC 2026-11-30 15:30 = 北京 2026-11-30 23:30
        assertThat(generator.generate(1L, "image/jpeg")).contains("/2026/11/");
    }

    @Test
    void rejectsUnsupportedType() {
        assertThatThrownBy(() -> generator.generate(1L, "image/svg+xml"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_TYPE_UNSUPPORTED);
    }
}
