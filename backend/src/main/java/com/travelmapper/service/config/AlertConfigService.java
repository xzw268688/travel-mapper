package com.travelmapper.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 告警配置解析：数据库优先，环境变量兜底。
 * 数据库不可用时逐项回退到环境变量，保证「数据库故障」本身仍能发出告警。
 */
@Service
public class AlertConfigService {

    private final ConfigService configService;
    private final boolean envEnabled;
    private final String envWebhookUrl;
    private final int envMinIntervalSeconds;

    public AlertConfigService(ConfigService configService,
                              @Value("${app.alert.enabled:true}") boolean envEnabled,
                              @Value("${app.alert.webhook-url:}") String envWebhookUrl,
                              @Value("${app.alert.min-interval-seconds:300}") int envMinIntervalSeconds) {
        this.configService = configService;
        this.envEnabled = envEnabled;
        this.envWebhookUrl = envWebhookUrl == null ? "" : envWebhookUrl.trim();
        this.envMinIntervalSeconds = Math.max(1, envMinIntervalSeconds);
    }

    public AlertConfig resolve() {
        boolean enabled = envEnabled;
        String webhookUrl = envWebhookUrl;
        int minIntervalSeconds = envMinIntervalSeconds;

        try {
            enabled = configService.getBoolean(ConfigKeys.ALERT_ENABLED, enabled);
        } catch (RuntimeException ignored) {
            // 数据库不可用时保留环境变量值。
        }
        try {
            String stored = configService.get(ConfigKeys.ALERT_WEBHOOK_URL);
            if (stored != null && !stored.isBlank()) {
                webhookUrl = stored.trim();
            }
        } catch (RuntimeException ignored) {
            // 数据库不可用时保留环境变量值。
        }
        try {
            minIntervalSeconds = Math.max(1,
                    configService.getInt(ConfigKeys.ALERT_MIN_INTERVAL_SECONDS, minIntervalSeconds));
        } catch (RuntimeException ignored) {
            // 数据库不可用时保留环境变量值。
        }
        return new AlertConfig(enabled, webhookUrl, minIntervalSeconds);
    }
}