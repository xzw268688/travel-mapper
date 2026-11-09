package com.travelmapper.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.service.auth.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AbstractIntegrationTest.CapturingMailConfig.class)
public abstract class AbstractIntegrationTest {

    @TestConfiguration
    static class CapturingMailConfig {
        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }

    public static final String TEST_AES_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    public static final String TEST_JWT_SECRET =
            "test-jwt-secret-0123456789abcdef0123456789abcdef";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected CapturingEmailSender capturingMail;

    @Autowired
    protected StringRedisTemplate redis;

    /**
     * 共享开发库可能被本地后端用其他 AES Key 写入过密文；测试固定使用 TEST_AES_KEY，
     * 因此每个用例前清空加密配置，避免跨环境污染导致解密失败。
     */
    @BeforeEach
    void resetEncryptedConfigSecrets() {
        jdbcTemplate.update("UPDATE sys_config SET config_value = '' WHERE encrypted = 1");
        // 所有用例共用 127.0.0.1，按 IP 的发码限额会跨用例累积，逐个用例清理。
        Set<String> keys = redis.keys("auth:code:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestInfrastructure::mysqlJdbcUrl);
        registry.add("spring.datasource.username", TestInfrastructure::mysqlUsername);
        registry.add("spring.datasource.password", TestInfrastructure::mysqlPassword);
        registry.add("spring.data.redis.host", TestInfrastructure::redisHost);
        registry.add("spring.data.redis.port", TestInfrastructure::redisPort);
        registry.add("app.crypto.aes-key", () -> TEST_AES_KEY);
        registry.add("app.security.jwt-secret", () -> TEST_JWT_SECRET);
        registry.add("app.security.admin-stub-enabled", () -> "true");
        registry.add("app.security.allow-insecure-dev-stub", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    protected MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder.header("X-User-Id", "1").header("X-User-Role", "ADMIN");
    }

    protected MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder) {
        return builder.header("X-User-Id", "2").header("X-User-Role", "USER");
    }

    protected String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
