package com.travelmapper.service.storage;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectKeyGeneratorTest {

    private final ObjectKeyGenerator generator = new ObjectKeyGenerator();

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
    void rejectsUnsupportedType() {
        assertThatThrownBy(() -> generator.generate(1L, "image/svg+xml"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_TYPE_UNSUPPORTED);
    }
}
