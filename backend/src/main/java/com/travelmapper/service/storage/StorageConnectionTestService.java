package com.travelmapper.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * 存储连接测试：使用临时 Provider 验证连通性、签名与读写权限，不修改全局配置。
 */
@Slf4j
@Service
public class StorageConnectionTestService {

    private static final String TEST_PREFIX = "healthcheck/";
    private static final byte[] TEST_BODY =
            "travelmapper-storage-test".getBytes(StandardCharsets.UTF_8);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public StorageTestResult test(StorageConfig config) {
        if (config == null || !config.isConfigured()) {
            return new StorageTestResult(false, 0,
                    "配置不完整：endpoint、bucket、accessKey、secretKey 不能为空");
        }
        long start = System.nanoTime();
        try (S3CompatibleStorageProvider provider = new S3CompatibleStorageProvider(config)) {
            StorageProvider.ProbeResult probe = provider.probe();
            if (!probe.reachable()) {
                return new StorageTestResult(false, elapsed(start), safe(probe.message()));
            }

            String key = TEST_PREFIX + UUID.randomUUID() + ".txt";
            StorageProvider.PresignedUpload upload =
                    provider.presignUpload(key, "text/plain", 60);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(upload.uploadUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .method(upload.method() == null ? "PUT" : upload.method(),
                            HttpRequest.BodyPublishers.ofByteArray(TEST_BODY));
            upload.headers().forEach(builder::header);
            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new StorageTestResult(false, elapsed(start),
                        "上传测试对象失败：HTTP " + response.statusCode());
            }

            boolean exists = provider.exists(key);
            provider.delete(key);
            if (!exists) {
                return new StorageTestResult(false, elapsed(start),
                        "上传成功但无法读取测试对象，请检查读取权限");
            }
            return new StorageTestResult(true, elapsed(start),
                    "连接成功，上传、读取与删除均正常");
        } catch (Exception ex) {
            log.warn("storage connection test failed", ex);
            return new StorageTestResult(false, elapsed(start), safe(ex.getMessage()));
        }
    }

    private long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String safe(String message) {
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}