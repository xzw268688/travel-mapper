package com.travelmapper.common.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class SecurityStartupValidator implements InitializingBean {

    private final AppProperties properties;

    public SecurityStartupValidator(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        AppProperties.Security security = properties.getSecurity();
        if (security.isAdminStubEnabled() && !security.isAllowInsecureDevStub()) {
            throw new IllegalStateException(
                    "检测到 admin-stub-enabled=true，但 allow-insecure-dev-stub=false；"
                            + "X-User-* 身份头仅允许本地开发显式开启");
        }
    }
}