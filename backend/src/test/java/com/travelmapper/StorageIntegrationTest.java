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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        String videoOrphan = "note/1/2025/06/" + UUID.randomUUID() + ".mp4";
        String videoReferenced = "note/1/2025/06/" + UUID.randomUUID() + ".mp4";
        String contentReferenced = "note/1/2025/06/" + UUID.randomUUID() + ".webm";
        String thumbReferenced = referenced + ".thumb.jpg";
        String thumbOrphan = orphan + ".thumb.jpg";
        upload(orphan);
        upload(referenced);
        upload(videoOrphan, "video/mp4");
        upload(videoReferenced, "video/mp4");
        upload(contentReferenced, "video/webm");
        upload(thumbReferenced);
        upload(thumbOrphan);

        TravelNote note = new TravelNote();
        note.setUserId(1L);
        note.setCountryCode("JP");
        note.setCityCode("TYO");
        note.setTitle("cleanup");
        note.setContent("<p>cleanup</p><video controls src=\"/objects/" + contentReferenced + "\"></video>");
        note.setContentText("cleanup");
        note.setImagesJson("[\"" + referenced + "\"]");
        note.setVideosJson("[\"" + videoReferenced + "\"]");
        note.setVisibility(Visibility.PRIVATE);
        note.setTakedown(false);
        note.setDeleted(0);
        travelNoteMapper.insert(note);

        cleanupService.cleanup(Instant.now().plusSeconds(60));

        assertThat(storageHolder.get().exists(orphan)).isFalse();
        assertThat(storageHolder.get().exists(videoOrphan)).isFalse();
        assertThat(storageHolder.get().exists(referenced)).isTrue();
        assertThat(storageHolder.get().exists(videoReferenced)).isTrue();
        assertThat(storageHolder.get().exists(contentReferenced)).isTrue();
        assertThat(storageHolder.get().exists(thumbReferenced)).isTrue();
        assertThat(storageHolder.get().exists(thumbOrphan)).isFalse();

        travelNoteMapper.deleteById(note.getId());
        storageHolder.get().delete(referenced);
        storageHolder.get().delete(videoReferenced);
        storageHolder.get().delete(contentReferenced);
        storageHolder.get().delete(thumbReferenced);
    }

    @Test
    void noteResponsesExposeThumbnailUrls() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1000 FROM `user`", Long.class);
        jdbcTemplate.update("INSERT INTO `user` (id, username, email, password_hash, nickname, "
                        + "avatar_preset, role, status, email_verified, token_version, deleted) "
                        + "VALUES (?, ?, ?, 'x', 'thumb', 'preset_01', 'USER', 'NORMAL', 1, 1, 0)",
                userId, "thumb_" + suffix, "thumb_" + suffix + "@example.com");
        String key = "note/" + userId + "/2025/06/" + UUID.randomUUID() + ".jpg";
        upload(key);
        String title = "缩略图测试_" + suffix;
        String body = "{\"title\":\"" + title + "\",\"content\":\"<p>带缩略图的正文。</p>\","
                + "\"images\":[\"" + key + "\"],\"coverImage\":\"" + key + "\","
                + "\"countryCode\":\"JP\",\"cityCode\":\"TYO\",\"visibility\":\"PRIVATE\"}";
        MvcResult created = mockMvc.perform(asUser(userId, post("/api/notes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        long noteId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(asUser(userId, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/notes/" + noteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images[0].thumbUrl").value(containsString(".thumb.jpg")));
        mockMvc.perform(asUser(userId, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/notes"))
                        .param("keyword", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].coverThumbUrl").value(containsString(".thumb.jpg")));

        MvcResult share = mockMvc.perform(asUser(userId, post("/api/notes/" + noteId + "/share"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expireDays\":7}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(share.getResponse().getContentAsString())
                .path("data").path("token").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/s/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverThumbUrl").value(containsString(".thumb.jpg")))
                .andExpect(jsonPath("$.data.imageThumbs[0]").value(containsString(".thumb.jpg")));

        jdbcTemplate.update("DELETE FROM note_share WHERE note_id = ?", noteId);
        jdbcTemplate.update("DELETE FROM travel_note WHERE id = ?", noteId);
        jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        storageHolder.get().delete(key);
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

    @Test
    void connectionTestUploadsAndCleansTempObject() throws Exception {
        mockMvc.perform(asAdmin(post("/api/admin/config/oss/test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"minio\",\"endpoint\":\""
                                + TestInfrastructure.objectStorageEndpoint()
                                + "\",\"region\":\"\",\"bucket\":\"travelmapper\",\"accessKey\":\""
                                + TestInfrastructure.objectStorageAccessKey() + "\",\"secretKey\":\""
                                + TestInfrastructure.objectStorageSecretKey()
                                + "\",\"pathStyle\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));

        assertThat(storageHolder.get().list("healthcheck/")).isEmpty();
    }

    @Test
    void presignsThumbnailForOwnImageAndRejectsOtherUsers() throws Exception {
        String thumbOf = "note/2/2025/06/" + UUID.randomUUID() + ".jpg";
        MvcResult result = mockMvc.perform(asUser(post("/api/upload/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"NOTE_IMAGE\",\"contentType\":\"image/jpeg\","
                                + "\"size\":1024,\"thumbOf\":\"" + thumbOf + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains(thumbOf + ".thumb.jpg");

        mockMvc.perform(asUser(post("/api/upload/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"NOTE_IMAGE\",\"contentType\":\"image/jpeg\","
                                + "\"size\":1024,\"thumbOf\":\"note/999/2025/06/x.jpg\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(asUser(post("/api/upload/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"NOTE_IMAGE\",\"contentType\":\"image/jpeg\","
                                + "\"size\":600000,\"thumbOf\":\"" + thumbOf + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2019));
    }

    @Test
    void presignAcceptsQuickTimeVideoAndRejectsUnknownVideo() throws Exception {
        MvcResult quickTime = mockMvc.perform(asUser(post("/api/upload/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"NOTE_VIDEO\",\"contentType\":\"video/quicktime\",\"size\":1024}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(quickTime.getResponse().getContentAsString()).contains(".mov");

        mockMvc.perform(asUser(post("/api/upload/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"NOTE_VIDEO\",\"contentType\":\"video/x-msvideo\",\"size\":1024}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2030));
    }

    private MockHttpServletRequestBuilder asUser(Long userId, MockHttpServletRequestBuilder builder) {
        return builder.header("X-User-Id", String.valueOf(userId)).header("X-User-Role", "USER");
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
        upload(key, "image/jpeg");
    }

    private void upload(String key, String contentType) throws Exception {
        StorageProvider.PresignedUpload upload = storageUrlService.presignUpload(key, contentType);
        assertThat(put(upload.uploadUrl(), key.getBytes(StandardCharsets.UTF_8), contentType).statusCode())
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
