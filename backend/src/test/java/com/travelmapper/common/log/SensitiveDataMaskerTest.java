package com.travelmapper.common.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void masksKeyValueText() {
        String masked = SensitiveDataMasker.mask(
                "password=abc123 token: xyz apiKey=sk-123 Authorization: Bearer eyJabc.def.ghi");
        assertThat(masked).doesNotContain("abc123", "xyz", "sk-123", "eyJabc.def.ghi");
        assertThat(masked).contains("password", "token", "apiKey", "Authorization");
    }

    @Test
    void masksJsonAndKeepsItValid() throws Exception {
        String json = "{\"apiKey\":\"sk-live-1234\",\"note\":\"ok\",\"secretKey\":\"minio-secret\"}";
        String masked = SensitiveDataMasker.mask(json);
        assertThat(masked).doesNotContain("sk-live-1234", "minio-secret");
        objectMapper.readTree(masked);
        assertThat(masked).contains("\"apiKey\":\"****\"");
    }

    @Test
    void masksJsonLiteralsAndKeepsItValid() throws Exception {
        String json = "{\"apiKey\":null,\"token\":123,\"secret\":false,\"note\":\"ok\"}";
        String masked = SensitiveDataMasker.mask(json);
        objectMapper.readTree(masked);
        assertThat(masked).contains("\"apiKey\":\"****\"");
        assertThat(masked).doesNotContain("123", "false");
    }

    @Test
    void leavesSafeTextUntouched() {
        String text = "provider=qwen results=3 elapsedMs=42";
        assertThat(SensitiveDataMasker.mask(text)).isEqualTo(text);
    }
}
