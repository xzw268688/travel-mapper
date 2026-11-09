package com.travelmapper.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityStartupValidatorTest {

    @Test
    void rejectsDevStubWithoutExplicitUnsafeFlag() {
        AppProperties properties = new AppProperties();
        properties.getSecurity().setAdminStubEnabled(true);
        properties.getSecurity().setAllowInsecureDevStub(false);

        assertThatThrownBy(() -> new SecurityStartupValidator(properties).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allow-insecure-dev-stub");
    }

    @Test
    void allowsExplicitlyEnabledLocalStub() {
        AppProperties properties = new AppProperties();
        properties.getSecurity().setAdminStubEnabled(true);
        properties.getSecurity().setAllowInsecureDevStub(true);

        assertThatCode(() -> new SecurityStartupValidator(properties).afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}