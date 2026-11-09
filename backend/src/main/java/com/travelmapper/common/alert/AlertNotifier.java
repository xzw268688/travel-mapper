package com.travelmapper.common.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.service.config.AlertConfig;
import com.travelmapper.service.config.AlertConfigService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 飞书群机器人告警：尽力而为、异步发送、同类告警去重。
 * Webhook 地址只从配置读取，不写入代码或日志。
 */
@Slf4j
@Service
public class AlertNotifier {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final AlertConfigService configService;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public AlertNotifier(ObjectMapper objectMapper, AlertConfigService configService) {
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "alert-notifier");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void notify(String key, String title, String message) {
        AlertConfig config = configService.resolve();
        if (!config.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long minIntervalMillis = Math.max(1, config.minIntervalSeconds()) * 1000L;
        Long previous = lastSent.get(key);
        if (previous != null && now - previous < minIntervalMillis) {
            return;
        }
        lastSent.put(key, now);

        String text = buildText(title, message);
        if (config.webhookUrl().isBlank()) {
            log.info("[alert] {}", text.replace('\n', ' '));
            return;
        }
        executor.submit(() -> sendNow(text, config.webhookUrl()));
    }

    /**
     * 使用已保存的配置同步发送测试消息；绕过去重，便于连续验证。
     */
    public boolean sendTest() {
        AlertConfig config = configService.resolve();
        if (!config.enabled() || config.webhookUrl().isBlank()) {
            return false;
        }
        return sendNow(buildText("告警通道测试", "这是一条测试消息，收到说明配置成功。"),
                config.webhookUrl());
    }

    private boolean sendNow(String text, String webhookUrl) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "msg_type", "text",
                    "content", Map.of("text", text)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("alert webhook returned status {}", response.statusCode());
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("alert webhook send failed", ex);
            return false;
        }
    }

    private String buildText(String title, String message) {
        return "【旅迹地图】" + title + "\n"
                + message + "\n"
                + "时间：" + LocalDateTime.now().format(TIME_FORMAT);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}