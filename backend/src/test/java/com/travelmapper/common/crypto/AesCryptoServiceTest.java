package com.travelmapper.common.crypto;

import com.travelmapper.common.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesCryptoServiceTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private AesCryptoService service() {
        AppProperties properties = new AppProperties();
        properties.getCrypto().setAesKey(KEY);
        return new AesCryptoService(properties);
    }

    @Test
    void encryptsAndDecrypts() {
        AesCryptoService service = service();
        String cipher = service.encrypt("sk-live-secret");
        assertThat(cipher).isNotEqualTo("sk-live-secret");
        assertThat(service.decrypt(cipher)).isEqualTo("sk-live-secret");
    }

    @Test
    void usesRandomIvSoCiphertextDiffers() {
        AesCryptoService service = service();
        assertThat(service.encrypt("same")).isNotEqualTo(service.encrypt("same"));
    }

    @Test
    void emptyValueStaysEmpty() {
        assertThat(service().encrypt("")).isEmpty();
        assertThat(service().decrypt("")).isEmpty();
    }

    @Test
    void rejectsTamperedCiphertext() {
        AesCryptoService service = service();
        String cipher = service.encrypt("secret");
        String tampered = cipher.substring(0, cipher.length() - 2) + "AA";
        assertThatThrownBy(() -> service.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingKeyFailsStartup() {
        AppProperties properties = new AppProperties();
        assertThatThrownBy(() -> new AesCryptoService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRAVELMAPPER_AES_KEY");
    }

    @Test
    void maskKeepsOnlyLastFourCharacters() {
        assertThat(AesCryptoService.mask("sk-live-1234")).isEqualTo("****1234");
        assertThat(AesCryptoService.mask("abc")).isEqualTo("****");
        assertThat(AesCryptoService.mask("")).isEmpty();
    }
}
