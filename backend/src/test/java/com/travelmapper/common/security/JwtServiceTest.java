package com.travelmapper.common.security;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-0123456789abcdef0123456789abcdef";

    private JwtService service(String secret, long ttl) {
        AppProperties properties = new AppProperties();
        properties.getSecurity().setJwtSecret(secret);
        properties.getSecurity().setJwtTtlSeconds(ttl);
        return new JwtService(properties);
    }

    @Test
    void issuesAndParsesClaims() {
        JwtService service = service(SECRET, 604800);
        String token = service.issue(42L, "USER", 3);
        JwtPayload payload = service.parse(token);
        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.role()).isEqualTo("USER");
        assertThat(payload.tokenVersion()).isEqualTo(3);
        assertThat(payload.jti()).isNotBlank();
    }

    @Test
    void everyIssuedTokenGetsAUniqueJti() {
        JwtService service = service(SECRET, 604800);
        String first = service.issue(42L, "USER", 3);
        String second = service.issue(42L, "USER", 3);
        assertThat(service.parse(first).jti()).isNotEqualTo(service.parse(second).jti());
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = service(SECRET, 604800);
        String token = service.issue(1L, "USER", 1);
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(BusinessException.class);
    }

    @Test
    void wrongSecretIsRejected() {
        JwtService issuer = service(SECRET, 604800);
        JwtService verifier = service(SECRET + "-different", 604800);
        String token = issuer.issue(1L, "USER", 1);
        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(BusinessException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = service(SECRET, -1);
        String token = service.issue(1L, "USER", 1);
        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(BusinessException.class);
    }

    @Test
    void missingOrShortSecretFailsStartup() {
        assertThatThrownBy(() -> new JwtService(new AppProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRAVELMAPPER_JWT_SECRET");
        assertThatThrownBy(() -> service("short", 100))
                .isInstanceOf(IllegalStateException.class);
    }
}
