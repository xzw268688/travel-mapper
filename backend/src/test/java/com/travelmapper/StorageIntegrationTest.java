package com.travelmapper;

import com.travelmapper.repository.entity.TravelNote;
import com.travelmapper.repository.enums.Visibility;
import com.travelmapper.repository.mapper.TravelNoteMapper;
import com.travelmapper.service.config.ConfigEntry;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import com.travelmapper.service.storage.StorageCleanupService;
import com.travelmapper.service.storage.StorageProvider;
import com.travelmapper.service.storage.StorageProviderHolder;
import com.travelmapper.service.storage.StorageUrlService;
import com.travelmapper.support.AbstractIntegrationTest;
import com.travelmapper.support.TestInfrastructure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorageIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ConfigService configService;

    @Autowired
    private StorageProviderHolder storageHolder;

    @Autowired
    private StorageUrlService storageUrlService;

    @Autowired
    private StorageCleanupService cleanupService;

    @Autowired
    private TravelNoteMapper travelNoteMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    static void requireMinio() {
        Assumptions.assumeTrue(TestInfrastructure.OBJECT_STORAGE_AVAILABLE, "MinIO image is not available");
    }

    @BeforeEach
    void configureStorage() {
        configureBucket("travelmapper");
    }

    @Test
    void presignsUploadDownloadAndShareTtls() throws Exception {
        String key = "note/1/2025/06/" + UUID.randomUUID() + ".jpg";
        Instant before = Instant.now();
        StorageProvider.PresignedUpload upload = storageUrlService.presignUpload(key, "image/jpeg");
        assertThat(upload.headers()).containsEntry("Content-Type", "image/jpeg");
        assertThat(Duration.between(before, upload.expireAt()).toSeconds()).isBetween(290L, 300L);

        byte[] content = "hello-minio".getBytes(StandardCharsets.UTF_8);
        assertThat(put(upload.uploadUrl(), content, "image/jpeg").statusCode()).isEqualTo(200);
        assertThat(storageHolder.get().exists(key)).isTrue();

        String downloadUrl = storageUrlService.presignDownload(key);
        assertThat(queryParam(downloadUrl, "X-Amz-Expires")).isEqualTo("600");
        HttpResponse<byte[]> downloaded = get(downloadUrl);
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(content);

        String shareUrl = storageUrlService.presignShareDownload(key, Instant.now().plus(Duration.ofHours(2)));
        assertThat(queryParam(shareUrl, "X-Amz-Expires")).isEqualTo("3600");

        String shortShareUrl = storageUrlService.presignShareDownload(key, Instant.now().plus(Duration.ofMinutes(10)));
        long expires = Long.parseLong(queryParam(shortShareUrl, "X-Amz-Expires"));
        assertThat(expires).isBetween(590L, 600L);

        storageHolder.get().delete(key);
        assertThat(storageHolder.get().exists(key)).isFalse();
    }

    @Test
    void privateBucketRejectsUnsignedAccess() throws Exception {
        Assumptions.assumeTrue(TestInfrastructure.PRIVATE_BUCKET_ENFORCED,
                "Current object storage backend does not enforce private buckets");
        String key = "note/1/2025/06/" + UUID.randomUUID() + ".jpg";
        StorageProvider.PresignedUpload upload = storageUrlService.presignUpload(key, "image/jpeg");
        assertThat(put(upload.uploadUrl(), "private".getBytes(StandardCharsets.UTF_8), "image/jpeg").statusCode())
                .isEqualTo(200);

        String unsigned = TestInfrastructure.objectStorageEndpoint() + "/travelmapper/" + key;
        assertThat(get(unsigned).statusCode()).isEqualTo(403);

        storageHolder.get().delete(key);
    }

    @Test
    void cleanupDeletesOrphansAndKeepsReferencedObjects() throws Exception {
        String orphan = "note/1/2025/06/" + UUID.randomUUID() + ".jpg";
        String referenced = "note/1/2025/06/" + UUID.randomUUID() + ".jpg";
        upload(orphan);
        upload(referenced);

        TravelNote note = new TravelNote();
        note.setUserId(1L);
        note.setCountryCode("JP");
        note.setCityCode("TYO");
        note.setTitle("cleanup");
        note.setContent("<p>cleanup</p>");
        note.setContentText("cleanup");
        note.setImagesJson("[\"" + referenced + "\"]");
        note.setVisibility(Visibility.PRIVATE);
        note.setTakedown(false);
        note.setDeleted(0);
        travelNoteMapper.insert(note);

        cleanupService.cleanup(Instant.now().plusSeconds(60));

        assertThat(storageHolder.get().exists(orphan)).isFalse();
        assertThat(storageHolder.get().exists(referenced)).isTrue();

        travelNoteMapper.deleteById(note.getId());
        storageHolder.get().delete(referenced);
    }

    @Test
    void storageBucketCanBeSwitchedWithoutRestart() {
        configureBucket("travelmapper2");
        String key = "note/1/2025/06/" + UUID.randomUUID() + ".jpg";
        StorageProvider.PresignedUpload upload = storageUrlService.presignUpload(key, "image/jpeg");
        assertThat(upload.uploadUrl()).contains("travelmapper2");

        configureBucket("travelmapper");
        assertThat(storageUrlService.presignUpload(key, "image/jpeg").uploadUrl())
                .contains("travelmapper");
    }

    private void configureBucket(String bucket) {
        Map<String, ConfigEntry> updates = new LinkedHashMap<>();
        updates.put(ConfigKeys.STORAGE_PROVIDER, ConfigEntry.plain("minio", "oss / minio"));
        updates.put(ConfigKeys.STORAGE_ENDPOINT, ConfigEntry.plain(TestInfrastructure.objectStorageEndpoint(), "服务端点"));
        updates.put(ConfigKeys.STORAGE_REGION, ConfigEntry.plain("", "MinIO 可空"));
        updates.put(ConfigKeys.STORAGE_BUCKET, ConfigEntry.plain(bucket, "桶名"));
        updates.put(ConfigKeys.STORAGE_ACCESS_KEY, ConfigEntry.secret(TestInfrastructure.objectStorageAccessKey(), "AES 加密存储"));
        updates.put(ConfigKeys.STORAGE_SECRET_KEY, ConfigEntry.secret(TestInfrastructure.objectStorageSecretKey(), "AES 加密存储"));
        updates.put(ConfigKeys.STORAGE_PATH_STYLE, ConfigEntry.plain("true", "MinIO=true"));
        configService.updateGroup(updates, 1L);
        storageHolder.reload();
    }

    private void upload(String key) throws Exception {
        StorageProvider.PresignedUpload upload = storageUrlService.presignUpload(key, "image/jpeg");
        assertThat(put(upload.uploadUrl(), key.getBytes(StandardCharsets.UTF_8), "image/jpeg").statusCode())
                .isEqualTo(200);
    }

    private HttpResponse<Void> put(String url, byte[] body, String contentType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private HttpResponse<byte[]> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private String queryParam(String url, String name) {
        String query = URI.create(url).getQuery();
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair[0].equals(name)) {
                return pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }
}
