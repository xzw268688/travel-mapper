package com.travelmapper.common.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService totp = new TotpService();

    /** RFC 6238 附录 B 的 SHA1 测试密钥。 */
    private final String rfcSecret = Base32.encode(
            "12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesRfc6238Vectors() {
        assertThat(totp.codeAt(rfcSecret, 59L)).isEqualTo("287082");
        assertThat(totp.codeAt(rfcSecret, 1_111_111_109L)).isEqualTo("081804");
        assertThat(totp.codeAt(rfcSecret, 1_111_111_111L)).isEqualTo("050471");
        assertThat(totp.codeAt(rfcSecret, 1_234_567_890L)).isEqualTo("005924");
        assertThat(totp.codeAt(rfcSecret, 2_000_000_000L)).isEqualTo("279037");
        assertThat(totp.codeAt(rfcSecret, 20_000_000_000L)).isEqualTo("353130");
    }

    @Test
    void verifyAcceptsCurrentCodeAndAdjacentWindow() {
        long now = 1_234_567_890L;
        assertThat(totp.verify(rfcSecret, "005924", java.time.Instant.ofEpochSecond(now))).isTrue();
        String nextWindow = totp.codeAt(rfcSecret, now + 30);
        assertThat(totp.verify(rfcSecret, nextWindow, java.time.Instant.ofEpochSecond(now))).isTrue();
    }

    @Test
    void verifyRejectsWrongCodeAndBadFormat() {
        long now = 1_234_567_890L;
        assertThat(totp.verify(rfcSecret, "000000", java.time.Instant.ofEpochSecond(now))).isFalse();
        assertThat(totp.verify(rfcSecret, "12345", java.time.Instant.ofEpochSecond(now))).isFalse();
        assertThat(totp.verify(rfcSecret, "abcdef", java.time.Instant.ofEpochSecond(now))).isFalse();
        assertThat(totp.verify("not-base32!", "005924", java.time.Instant.ofEpochSecond(now))).isFalse();
    }

    @Test
    void generatedSecretsAreBase32RoundTrippable() {
        String secret = totp.generateSecret();
        assertThat(secret).hasSize(32);
        assertThat(Base32.encode(Base32.decode(secret))).isEqualTo(secret);
    }

    @Test
    void otpauthUriContainsIssuerAndSecret() {
        String uri = totp.otpauthUri("TravelMapper", "user@example.com", rfcSecret);
        assertThat(uri).startsWith("otpauth://totp/TravelMapper:user%40example.com?");
        assertThat(uri).contains("secret=" + rfcSecret);
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
    }
}