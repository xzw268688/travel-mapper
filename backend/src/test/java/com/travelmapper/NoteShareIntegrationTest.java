package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.repository.entity.User;
import com.travelmapper.service.config.ConfigEntry;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import com.travelmapper.service.storage.StorageProvider;
import com.travelmapper.service.storage.StorageProviderHolder;
import com.travelmapper.support.AuthTestSupport;
import com.travelmapper.support.TestInfrastructure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NoteShareIntegrationTest extends AuthTestSupport {

    @Autowired
    private ConfigService configService;

    @Autowired
    private StorageProviderHolder storageHolder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    static void requireStorage() {
        Assumptions.assumeTrue(TestInfrastructure.OBJECT_STORAGE_AVAILABLE,
                "object storage backend unavailable");
    }

    @BeforeEach
    void configureStorage() {
        Map<String, ConfigEntry> updates = new LinkedHashMap<>();
        updates.put(ConfigKeys.STORAGE_PROVIDER, ConfigEntry.plain("minio", "test"));
        updates.put(ConfigKeys.STORAGE_ENDPOINT,
                ConfigEntry.plain(TestInfrastructure.objectStorageEndpoint(), "test"));
        updates.put(ConfigKeys.STORAGE_REGION, ConfigEntry.plain("", "test"));
        updates.put(ConfigKeys.STORAGE_BUCKET, ConfigEntry.plain("travelmapper", "test"));
        updates.put(ConfigKeys.STORAGE_ACCESS_KEY,
                ConfigEntry.secret(TestInfrastructure.objectStorageAccessKey(), "test"));
        updates.put(ConfigKeys.STORAGE_SECRET_KEY,
                ConfigEntry.secret(TestInfrastructure.objectStorageSecretKey(), "test"));
        updates.put(ConfigKeys.STORAGE_PATH_STYLE, ConfigEntry.plain("true", "test"));
        configService.updateGroup(updates, 1L);
        storageHolder.reload();
    }

    @Test
    void uploadPresignValidatesTypeSizeAndKeyFormat() throws Exception {
        User user = createVerifiedUser("upload");
        MvcResult result = mockMvc.perform(withToken(post("/api/upload/presign"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"NOTE_IMAGE\",\"contentType\":\"image/jpeg\",\"size\":1024}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("PUT"))
                .andExpect(jsonPath("$.data.uploadUrl").isString())
                .andReturn();
        String objectKey = data(result).path("objectKey").asText();
        assertThat(objectKey).startsWith("note/" + user.getId() + "/").endsWith(".jpg");

        mockMvc.perform(withToken(post("/api/upload/presign"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"NOTE_IMAGE\",\"contentType\":\"image/svg+xml\",\"size\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2021));
        mockMvc.perform(withToken(post("/api/upload/presign"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"NOTE_IMAGE\",\"contentType\":\"image/png\",\"size\":5242881}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2019));
        mockMvc.perform(withToken(post("/api/upload/presign"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"OTHER\",\"contentType\":\"image/png\",\"size\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void uploadPresignSupportsVideoWithThirtyMinuteTtl() throws Exception {
        User user = createVerifiedUser("uploadvideo");
        Instant before = Instant.now();
        MvcResult result = mockMvc.perform(withToken(post("/api/upload/presign"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"NOTE_VIDEO\",\"contentType\":\"video/mp4\",\"size\":1048576}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("PUT"))
                .andReturn();
        JsonNode data = data(result);
        assertThat(data.path("objectKey").asText())
                .startsWith("note/" + user.getId() + "/").endsWith(".mp4");
        Instant expireAt = Instant.parse(data.path("expireAt").asText());
        assertThat(Duration.between(before, expireAt).toSeconds()).isBetween(1790L, 1800L);

        mockMvc.perform(withToken(post("/api/upload/presign"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"NOTE_VIDEO\",\"contentType\":\"video/x-msvideo\",\"size\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2030));
        mockMvc.perform(withToken(post("/api/upload/presign"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"NOTE_VIDEO\",\"contentType\":\"video/webm\",\"size\":104857601}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2029));
    }

    @Test
    void noteVideosAreValidatedStoredAndReturned() throws Exception {
        User user = createVerifiedUser("notevideo");
        String imageKey = uploadObject(user, "image/jpeg", "jpg", new byte[]{9, 9, 9});
        String videoKey = uploadObject(user, "video/mp4", "mp4", new byte[]{0, 1, 2, 3});
        String content = "<p>视频游记</p><img src=\"/objects/" + imageKey + "\">"
                + "<video controls src=\"/objects/" + videoKey + "\"></video>";

        Map<String, Object> body = noteBody("视频游记", content, List.of(imageKey), null);
        body.put("videos", List.of(videoKey));
        MvcResult created = mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        long noteId = data(created).path("id").asLong();

        String stored = jdbcTemplate.queryForObject(
                "SELECT content FROM travel_note WHERE id = ?", String.class, noteId);
        assertThat(stored).contains("<video", "/objects/" + videoKey)
                .doesNotContain("javascript:");
        mockMvc.perform(withToken(get("/api/notes/" + noteId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videos.length()").value(1))
                .andExpect(jsonPath("$.data.videos[0].objectKey").value(videoKey))
                .andExpect(jsonPath("$.data.videos[0].url").isString())
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andExpect(jsonPath("$.data.images[0].objectKey").value(imageKey));

        // 视频-only 正文允许保存
        Map<String, Object> videoOnly = noteBody("纯视频", "<video controls src=\"/objects/"
                + videoKey + "\"></video>", List.of(), null);
        videoOnly.put("videos", List.of(videoKey));
        mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(videoOnly)))
                .andExpect(status().isOk());

        // 视频数量上限
        List<String> four = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            four.add(videoKey);
        }
        Map<String, Object> tooMany = noteBody("太多视频", "<p>x</p>", List.of(), null);
        tooMany.put("videos", four);
        mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(tooMany)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2028));
    }

    @Test
    void referencedMediaMustExistAndBelongToUser() throws Exception {
        User user = createVerifiedUser("notemedia");
        String missing = "note/" + user.getId() + "/2026/11/" + UUID.randomUUID() + ".jpg";
        Map<String, Object> missingBody = noteBody("缺失媒体", "<p>x</p>", List.of(missing), null);
        mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(missingBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2027));

        Map<String, Object> foreignBody = noteBody("越权媒体", "<p>x</p>",
                List.of("note/999999/2026/11/foreign.jpg"), null);
        mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(foreignBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2027));
    }

    @Test
    void createNoteSanitizesXssAndLightsMapGreen() throws Exception {
        User user = createVerifiedUser("notecreate");
        configService.updateGroup(Map.of("sensitive.words",
                ConfigEntry.plain("敏感词", "test")), 1L);
        try {
            String content = "<p>Hello<script>alert(1)</script>"
                    + "<a href=\"javascript:alert(1)\" onclick=\"bad()\">link</a> 敏感词</p>"
                    + "<img src=\"/objects/note/1/2026/10/abc.jpg\" alt=\"游记图片\">";
            Map<String, Object> body = noteBody("东京散步", content, List.of(), null);
            MvcResult created = mockMvc.perform(withToken(post("/api/notes"), user)
                            .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.countryStatusAfter").value("GREEN"))
                    .andReturn();
            long noteId = data(created).path("id").asLong();
            String stored = jdbcTemplate.queryForObject(
                    "SELECT content FROM travel_note WHERE id = ?", String.class, noteId);
            assertThat(stored).doesNotContain("script", "javascript:", "onclick").contains("***");
            assertThat(stored).contains("/objects/note/1/2026/10/abc.jpg");
            String text = jdbcTemplate.queryForObject(
                    "SELECT content_text FROM travel_note WHERE id = ?", String.class, noteId);
            assertThat(text).contains("Hello").doesNotContain("敏感词");

            mockMvc.perform(withToken(get("/api/notes/" + noteId), user))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.takedown").value(false))
                    .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                    .andExpect(jsonPath("$.data.cityName").value("东京"));
            mockMvc.perform(withToken(get("/api/map/stats"), user))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.countries[?(@.countryCode == 'JP')].status")
                            .value(org.hamcrest.Matchers.hasItem("GREEN")));
            User other = createVerifiedUser("notecreate2");
            mockMvc.perform(withToken(get("/api/notes/" + noteId), other))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(2004));
        } finally {
            configService.updateGroup(Map.of("sensitive.words",
                    ConfigEntry.plain("", "test")), 1L);
        }
    }

    @Test
    void noteItineraryAssociationAndManualTripRules() throws Exception {
        User user = createVerifiedUser("noteassoc");
        long tripId = insertTrip(user, "JP", "日本旅行");
        long itineraryId = insertItinerary(user, tripId, "JP", "东京行程");

        Map<String, Object> body = noteBody("关联游记", "<p>Hello</p>", List.of(), itineraryId);
        body.put("countryCode", "TH");
        body.put("cityCode", "BKK");
        MvcResult created = mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripInherited").value(true))
                .andExpect(jsonPath("$.data.tripId").value(tripId))
                .andReturn();
        long noteId = data(created).path("id").asLong();
        mockMvc.perform(withToken(get("/api/notes/" + noteId), user))
                .andExpect(jsonPath("$.data.countryCode").value("JP"))
                .andExpect(jsonPath("$.data.cityCode").value("TYO"));

        long thTrip = insertTrip(user, "TH", "泰国旅行");
        Map<String, Object> cross = noteBody("跨国", "<p>x</p>", List.of(), null);
        cross.put("tripId", thTrip);
        mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(cross)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        // 同国且日期落在旅行区间内：自动归到该旅行
        Map<String, Object> auto = noteBody("自动归组", "<p>x</p>", List.of(), null);
        MvcResult autoResult = mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(auto)))
                .andExpect(status().isOk()).andReturn();
        assertThat(data(autoResult).path("tripId").asLong()).isEqualTo(tripId);

        // 该国没有归档：自动新建一个单日归档，不再留在未归组
        Map<String, Object> autoCreated = noteBody("自动归档", "<p>x</p>", List.of(), null);
        autoCreated.put("countryCode", "US");
        autoCreated.put("cityCode", "NYC");
        MvcResult createdResult = mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(autoCreated)))
                .andExpect(status().isOk()).andReturn();
        assertThat(data(createdResult).path("tripId").isNull()).isFalse();
        assertThat(data(createdResult).path("tripInherited").asBoolean()).isFalse();
    }

    @Test
    void shareLifecycleWhitelistAndTakedown() throws Exception {
        User user = createVerifiedUser("notes");
        long noteId = createNote(user, "分享游记", "<p>分享内容</p>",
                List.of(), null);

        MvcResult shared = mockMvc.perform(withToken(post("/api/notes/" + noteId + "/share"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expireDays\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(true))
                .andReturn();
        String token = data(shared).path("token").asText();
        mockMvc.perform(withToken(post("/api/notes/" + noteId + "/share"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expireDays\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.token").value(token));

        MvcResult page = mockMvc.perform(get("/s/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("分享游记"))
                .andExpect(jsonPath("$.data.nicknameMasked").value(
                        org.hamcrest.Matchers.endsWith("*")))
                .andReturn();
        JsonNode publicData = data(page);
        assertThat(publicData.has("content")).isFalse();
        assertThat(publicData.has("tripId")).isFalse();
        assertThat(publicData.has("tripName")).isFalse();
        assertThat(publicData.has("email")).isFalse();
        assertThat(publicData.path("summary").asText()).doesNotContain("<p>");
        assertThat(publicData.path("images").size()).isLessThanOrEqualTo(6);

        mockMvc.perform(withToken(delete("/api/notes/" + noteId + "/share"), user))
                .andExpect(status().isOk());
        mockMvc.perform(get("/s/" + token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(2007));

        MvcResult again = mockMvc.perform(withToken(post("/api/notes/" + noteId + "/share"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expireDays\":7}"))
                .andExpect(status().isOk()).andReturn();
        String newToken = data(again).path("token").asText();
        assertThat(newToken).isNotEqualTo(token);

        mockMvc.perform(asAdmin(put("/api/admin/notes/" + noteId + "/takedown"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"违规\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.takedown").value(true));
        mockMvc.perform(withToken(get("/api/notes/" + noteId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.takedown").value(true));
        mockMvc.perform(withToken(put("/api/notes/" + noteId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"<p>不能改</p>\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2024));
        mockMvc.perform(withToken(post("/api/notes/" + noteId + "/share"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2024));
        mockMvc.perform(get("/s/" + newToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(2007));

        mockMvc.perform(asAdmin(put("/api/admin/notes/" + noteId + "/restore")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.takedown").value(false));
        mockMvc.perform(get("/s/" + newToken))
                .andExpect(status().isGone());
        MvcResult restoredShare = mockMvc.perform(withToken(post("/api/notes/" + noteId + "/share"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expireDays\":7}"))
                .andExpect(status().isOk()).andReturn();
        String restoredToken = data(restoredShare).path("token").asText();
        mockMvc.perform(get("/s/" + restoredToken)).andExpect(status().isOk());

        mockMvc.perform(withToken(delete("/api/notes/" + noteId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.countryStatusAfter").value("GRAY"))
                .andExpect(jsonPath("$.data.sharesRevoked").value(1));
        mockMvc.perform(get("/s/" + restoredToken)).andExpect(status().isGone());
    }

    @Test
    void noteLimitsAndCoverValidation() throws Exception {
        User user = createVerifiedUser("notelimits");
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tooMany.add("note/1/" + i + ".jpg");
        }
        mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(noteBody("超多图", "<p>x</p>", tooMany, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2018));
        String huge = "a".repeat(200 * 1024 + 1);
        mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(noteBody("超长", huge, List.of(), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2020));
        Map<String, Object> badCover = noteBody("封面", "<p>x</p>", List.of("note/1/a.jpg"), null);
        badCover.put("coverImage", "note/1/b.jpg");
        mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(badCover)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    private String uploadObject(User user, String contentType, String extension, byte[] content)
            throws Exception {
        String key = "note/" + user.getId() + "/2026/11/" + UUID.randomUUID() + "." + extension;
        StorageProvider.PresignedUpload upload = storageHolder.get()
                .presignUpload(key, contentType, 300);
        HttpRequest request = HttpRequest.newBuilder(URI.create(upload.uploadUrl()))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        assertThat(httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode())
                .isEqualTo(200);
        return key;
    }

    private long createNote(User user, String title, String content, List<String> images,
                            Long itineraryId) throws Exception {
        MvcResult result = mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(noteBody(title, content, images, itineraryId))))
                .andExpect(status().isOk()).andReturn();
        return data(result).path("id").asLong();
    }

    private Map<String, Object> noteBody(String title, String content, List<String> images,
                                         Long itineraryId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("images", images);
        body.put("coverImage", images.isEmpty() ? null : images.get(0));
        body.put("countryCode", "JP");
        body.put("cityCode", "TYO");
        body.put("visibility", "PRIVATE");
        if (itineraryId != null) {
            body.put("itineraryId", itineraryId);
        }
        return body;
    }

    private long insertTrip(User user, String country, String name) {
        jdbcTemplate.update("INSERT INTO trip (user_id, country_code, name, start_date, end_date, deleted) "
                + "VALUES (?, ?, ?, ?, ?, 0)", user.getId(), country, name,
                LocalDate.now(), LocalDate.now().plusDays(2));
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM trip WHERE user_id = ?", Long.class, user.getId());
    }

    private long insertItinerary(User user, long tripId, String country, String title) {
        jdbcTemplate.update("INSERT INTO itinerary (user_id, trip_id, country_code, title, start_date, "
                        + "end_date, status, deleted) VALUES (?, ?, ?, ?, ?, ?, 'SAVED', 0)",
                user.getId(), tripId, country, title, LocalDate.now(), LocalDate.now().plusDays(1));
        long itineraryId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM itinerary WHERE user_id = ?", Long.class, user.getId());
        jdbcTemplate.update("INSERT INTO itinerary_day (itinerary_id, day_index, visit_date, city_code) "
                + "VALUES (?, 1, ?, 'TYO')", itineraryId, LocalDate.now());
        return itineraryId;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
}
