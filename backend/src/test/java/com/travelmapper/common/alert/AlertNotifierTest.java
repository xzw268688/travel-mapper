package com.travelmapper.common.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.service.config.AlertConfig;
import com.travelmapper.service.config.AlertConfigService;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertNotifierTest {

    private final TimeProvider timeProvider = new TimeProvider(
            Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), TimeProvider.BEIJING));

    @Test
    void sendsFeishuTextAndSuppressesDuplicateKeys() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
        AlertConfigService configService = mock(AlertConfigService.class);
        when(configService.resolve()).thenReturn(new AlertConfig(true, url, 300));
        AlertNotifier notifier = new AlertNotifier(new ObjectMapper(), configService, timeProvider);
        try {
            notifier.notify("error:unexpected", "未捕获异常", "NullPointerException");
            String first = received.poll(3, TimeUnit.SECONDS);
            assertThat(first).isNotNull();
            assertThat(first).contains("msg_type", "text", "未捕获异常", "NullPointerException");

            notifier.notify("error:unexpected", "未捕获异常", "NullPointerException");
            assertThat(received.poll(400, TimeUnit.MILLISECONDS)).isNull();

            notifier.notify("health:数据库", "数据库 不可用", "连接失败");
            assertThat(received.poll(3, TimeUnit.SECONDS)).contains("数据库 不可用");

            assertThat(notifier.sendTest()).isTrue();
            assertThat(received.poll(3, TimeUnit.SECONDS)).contains("告警通道测试");
        } finally {
            notifier.shutdown();
            server.stop(0);
        }
    }

    @Test
    void blankWebhookIsNoOp() {
        AlertConfigService configService = mock(AlertConfigService.class);
        when(configService.resolve()).thenReturn(new AlertConfig(true, "", 300));
        AlertNotifier notifier = new AlertNotifier(new ObjectMapper(), configService, timeProvider);
        try {
            assertThatCode(() -> notifier.notify("k", "标题", "内容"))
                    .doesNotThrowAnyException();
            assertThat(notifier.sendTest()).isFalse();
        } finally {
            notifier.shutdown();
        }
    }
}