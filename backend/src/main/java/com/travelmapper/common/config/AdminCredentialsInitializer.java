package com.travelmapper.common.config;

import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 生产环境禁止使用种子管理员默认密码；可通过 TRAVELMAPPER_ADMIN_PASSWORD 注入新密码。
 */
@Slf4j
@Component
public class AdminCredentialsInitializer implements InitializingBean {

    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@123456";

    private final AppProperties properties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminCredentialsInitializer(AppProperties properties, UserMapper userMapper,
                                       PasswordEncoder passwordEncoder, Environment environment) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        AppProperties.Security security = properties.getSecurity();
        User admin = userMapper.selectByAccountIncludingDeleted(security.getAdminUsername());
        String configuredPassword = security.getAdminPassword();

        if (configuredPassword != null && !configuredPassword.isBlank()) {
            if (admin == null) {
                log.warn("未找到管理员账号 {}，跳过管理员密码初始化", security.getAdminUsername());
                return;
            }
            if (!passwordEncoder.matches(configuredPassword, admin.getPasswordHash())) {
                admin.setPasswordHash(passwordEncoder.encode(configuredPassword));
                admin.setTokenVersion((admin.getTokenVersion() == null ? 1 : admin.getTokenVersion()) + 1);
                userMapper.updateById(admin);
                log.info("已根据 TRAVELMAPPER_ADMIN_PASSWORD 更新管理员密码");
            }
            return;
        }

        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        if (production && admin != null
                && passwordEncoder.matches(DEFAULT_ADMIN_PASSWORD, admin.getPasswordHash())) {
            throw new IllegalStateException(
                    "检测到管理员仍在使用默认密码，请设置 TRAVELMAPPER_ADMIN_PASSWORD 后重启");
        }
    }
}