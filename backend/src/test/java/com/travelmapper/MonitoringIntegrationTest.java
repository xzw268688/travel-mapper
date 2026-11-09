package com.travelmapper;

import com.travelmapper.repository.entity.User;
import com.travelmapper.service.config.ConfigEntry;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import com.travelmapper.service.storage.StorageProviderHolder;
import com.travelmapper.support.AbstractIntegrationTest;
import com.travelmapper.support.AuthTestSupport;
import com.travelmapper.support.TestInfrastructure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonitoringIntegrationTest extends AuthTestSupport {

    @Autowired
    private ConfigService configService;

    @Autowired
    private StorageProviderHolder storageHolder;

    @Test
    void onlineUsersAreListedAndKicked() throws Exception {
        User user = createVerifiedUser("online");
        String token = bearer(user);

        mockMvc.perform(get("/api/user/profile").header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(get("/api/admin/monitor/online-users"))
                        .param("keyword", user.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].userId").value(user.getId()))
                .andExpect(jsonPath("$.data.list[0].role").value("USER"));

        mockMvc.perform(asAdmin(delete("/api/admin/monitor/online-users/" + user.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/profile").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        assertThat(count("audit_log", "action = 'USER_KICK' AND target_id = ?", user.getId()))
                .isEqualTo(1);
    }

    @Test
    void serverDatabaseCacheAndDocsAreAvailable() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/monitor/server")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.osName").isString())
                .andExpect(jsonPath("$.data.jvm.threadCount").isNumber())
                .andExpect(jsonPath("$.data.disks").isArray());

        mockMvc.perform(asAdmin(get("/api/admin/monitor/database")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxPoolSize").isNumber());

        mockMvc.perform(asAdmin(get("/api/admin/monitor/cache")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.usedMemory").isString())
                .andExpect(jsonPath("$.data.groups").isArray())
                .andExpect(jsonPath("$.data.groups[0].label").isString());

        mockMvc.perform(asAdmin(get("/api/admin/monitor/api-docs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openapi").isString())
                .andExpect(jsonPath("$.data.paths").isMap());

        mockMvc.perform(asUser(get("/api/admin/monitor/server")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));

        mockMvc.perform(get("/api/admin/monitor/cache"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    void cacheClearOnlyAllowsSafeGroups() throws Exception {
        String safeKey = "login:fail:monitor-test-" + System.nanoTime();
        redis.opsForValue().set(safeKey, "3");

        mockMvc.perform(asAdmin(delete("/api/admin/monitor/cache/login-fail")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.group").value("login-fail"));
        assertThat(redis.hasKey(safeKey)).isFalse();

        String protectedKey = "quota:free:monitor-test";
        redis.opsForValue().set(protectedKey, "1");
        try {
            mockMvc.perform(asAdmin(delete("/api/admin/monitor/cache/quota")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(2001));
            assertThat(redis.hasKey(protectedKey)).isTrue();
        } finally {
            redis.delete(protectedKey);
        }

        assertThat(count("audit_log", "action = 'CACHE_CLEAR'")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void storageMetricsAndStats() throws Exception {
        Assumptions.assumeTrue(TestInfrastructure.OBJECT_STORAGE_AVAILABLE,
                "object storage unavailable");
        configureStorage();

        mockMvc.perform(asAdmin(get("/api/admin/monitor/storage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.reachable").value(true))
                .andExpect(jsonPath("$.data.bucket").value("travelmapper"));

        mockMvc.perform(asAdmin(get("/api/admin/monitor/storage/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.objectCount").isNumber());
    }

    private void configureStorage() {
        Map<String, ConfigEntry> updates = new LinkedHashMap<>();
        updates.put(ConfigKeys.STORAGE_PROVIDER, ConfigEntry.plain("minio", "oss / minio"));
        updates.put(ConfigKeys.STORAGE_ENDPOINT,
                ConfigEntry.plain(TestInfrastructure.objectStorageEndpoint(), "服务端点"));
        updates.put(ConfigKeys.STORAGE_REGION, ConfigEntry.plain("", "MinIO 可空"));
        updates.put(ConfigKeys.STORAGE_BUCKET, ConfigEntry.plain("travelmapper", "桶名"));
        updates.put(ConfigKeys.STORAGE_ACCESS_KEY,
                ConfigEntry.secret(TestInfrastructure.objectStorageAccessKey(), "AES 加密存储"));
        updates.put(ConfigKeys.STORAGE_SECRET_KEY,
                ConfigEntry.secret(TestInfrastructure.objectStorageSecretKey(), "AES 加密存储"));
        updates.put(ConfigKeys.STORAGE_PATH_STYLE, ConfigEntry.plain("true", "MinIO=true"));
        configService.updateGroup(updates, 1L);
        storageHolder.reload();
    }
}