package com.travelmapper.service.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertConfigServiceTest {

    private static final String ENV_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/env";

    @Test
    void databaseValuesWin() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBoolean(eq(ConfigKeys.ALERT_ENABLED), anyBoolean())).thenReturn(false);
        when(configService.get(ConfigKeys.ALERT_WEBHOOK_URL)).thenReturn(
                "https://open.feishu.cn/open-apis/bot/v2/hook/db");
        when(configService.getInt(eq(ConfigKeys.ALERT_MIN_INTERVAL_SECONDS), anyInt())).thenReturn(60);

        AlertConfig config = new AlertConfigService(configService, true, ENV_URL, 300).resolve();

        assertThat(config.enabled()).isFalse();
        assertThat(config.webhookUrl()).endsWith("/db");
        assertThat(config.minIntervalSeconds()).isEqualTo(60);
    }

    @Test
    void fallsBackToEnvironmentWhenDatabaseFails() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBoolean(eq(ConfigKeys.ALERT_ENABLED), anyBoolean()))
                .thenThrow(new RuntimeException("db down"));
        when(configService.get(ConfigKeys.ALERT_WEBHOOK_URL))
                .thenThrow(new RuntimeException("db down"));
        when(configService.getInt(eq(ConfigKeys.ALERT_MIN_INTERVAL_SECONDS), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        AlertConfig config = new AlertConfigService(configService, true, ENV_URL, 300).resolve();

        assertThat(config.enabled()).isTrue();
        assertThat(config.webhookUrl()).isEqualTo(ENV_URL);
        assertThat(config.minIntervalSeconds()).isEqualTo(300);
    }

    @Test
    void blankStoredUrlUsesEnvironmentFallback() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBoolean(eq(ConfigKeys.ALERT_ENABLED), anyBoolean())).thenReturn(true);
        when(configService.get(ConfigKeys.ALERT_WEBHOOK_URL)).thenReturn("  ");
        when(configService.getInt(eq(ConfigKeys.ALERT_MIN_INTERVAL_SECONDS), anyInt())).thenReturn(300);

        AlertConfig config = new AlertConfigService(configService, true, ENV_URL, 300).resolve();

        assertThat(config.webhookUrl()).isEqualTo(ENV_URL);
    }
}