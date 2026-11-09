package com.travelmapper.common.config;

import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCredentialsInitializerTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final Environment environment = mock(Environment.class);

    @Test
    void productionRejectsSeededDefaultPassword() {
        AppProperties properties = new AppProperties();
        User admin = admin("hash");
        when(userMapper.selectByAccountIncludingDeleted("admin")).thenReturn(admin);
        when(passwordEncoder.matches("Admin@123456", "hash")).thenReturn(true);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        assertThatThrownBy(() -> initializer(properties).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRAVELMAPPER_ADMIN_PASSWORD");
    }

    @Test
    void configuredPasswordIsAppliedAndInvalidatesOldSessions() {
        AppProperties properties = new AppProperties();
        properties.getSecurity().setAdminPassword("new-password");
        User admin = admin("hash");
        when(userMapper.selectByAccountIncludingDeleted("admin")).thenReturn(admin);
        when(passwordEncoder.matches(eq("new-password"), eq("hash"))).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        initializer(properties).afterPropertiesSet();

        verify(userMapper).updateById(admin);
        assertThat(admin.getPasswordHash()).isEqualTo("new-hash");
        assertThat(admin.getTokenVersion()).isEqualTo(2);
    }

    @Test
    void localProfileMayKeepSeedPassword() {
        AppProperties properties = new AppProperties();
        User admin = admin("hash");
        when(userMapper.selectByAccountIncludingDeleted("admin")).thenReturn(admin);
        when(passwordEncoder.matches("Admin@123456", "hash")).thenReturn(true);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        assertThatCode(() -> initializer(properties).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private AdminCredentialsInitializer initializer(AppProperties properties) {
        return new AdminCredentialsInitializer(properties, userMapper, passwordEncoder, environment);
    }

    private User admin(String passwordHash) {
        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordHash);
        admin.setTokenVersion(1);
        return admin;
    }
}