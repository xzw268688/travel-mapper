package com.travelmapper.support;

import com.travelmapper.common.security.JwtService;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.Role;
import com.travelmapper.repository.enums.UserStatus;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.service.auth.LoginAttemptService;
import com.travelmapper.service.card.CardKeyService;
import com.travelmapper.service.config.ConfigService;
import com.travelmapper.service.quota.QuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

public abstract class AuthTestSupport extends AbstractIntegrationTest {

    protected static final String DEFAULT_PASSWORD = "Password123";

    @Autowired
    protected UserMapper userMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected QuotaService quotaService;

    @Autowired
    protected CardKeyService cardKeyService;

    @Autowired
    protected ConfigService configService;

    @Autowired
    protected LoginAttemptService loginAttemptService;

    protected User createVerifiedUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(prefix + "_" + suffix);
        user.setEmail(prefix + "_" + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setNickname(prefix);
        user.setAvatarPreset("preset_01");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.NORMAL);
        user.setEmailVerified(true);
        user.setTokenVersion(1);
        userMapper.insert(user);
        return user;
    }

    protected String bearer(User user) {
        User current = userMapper.selectById(user.getId());
        return "Bearer " + jwtService.issue(
                current.getId(), current.getRole().name(), current.getTokenVersion());
    }

    protected MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, User user) {
        return builder.header("Authorization", bearer(user));
    }

    protected String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected void clearEmailRateLimit(String email) {
        String normalized = email.toLowerCase();
        String day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        redis.delete("rate:email:" + normalized);
        redis.delete("auth:code:gap:" + normalized);
        redis.delete("auth:code:daily:" + normalized + ":" + day);
        redis.delete("auth:code:verify:" + normalized);
        redis.delete("auth:code:reset:" + normalized);
    }

    protected long count(String table, String where, Object... args) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `" + table + "` WHERE " + where, Long.class, args);
        return value == null ? 0 : value;
    }
}
