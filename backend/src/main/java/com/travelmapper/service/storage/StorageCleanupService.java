package com.travelmapper.service.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.repository.mapper.TravelNoteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class StorageCleanupService {

    private final StorageProviderHolder storage;
    private final TravelNoteMapper travelNoteMapper;
    private final ObjectMapper objectMapper;

    public StorageCleanupService(StorageProviderHolder storage, TravelNoteMapper travelNoteMapper,
                                 ObjectMapper objectMapper) {
        this.storage = storage;
        this.travelNoteMapper = travelNoteMapper;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${app.storage.orphan-cleanup-cron:0 30 3 * * *}")
    public void scheduledCleanup() {
        cleanup(Instant.now().minus(Duration.ofHours(24)));
    }

    public int cleanup(Instant cutoff) {
        StorageProvider provider = storage.get();
        List<StorageProvider.StoredObject> objects = provider.list("note/");
        if (objects.isEmpty()) {
            return 0;
        }
        Set<String> referenced = referencedObjects();
        int deleted = 0;
        for (StorageProvider.StoredObject object : objects) {
            if (object.lastModified() != null
                    && object.lastModified().isBefore(cutoff)
                    && !referenced.contains(object.key())) {
                provider.delete(object.key());
                deleted++;
            }
        }
        log.info("storage cleanup deleted {} orphan objects", deleted);
        return deleted;
    }

    private static final Pattern OBJECT_REFERENCE =
            Pattern.compile("/objects/([^\"'\\s<>]+)");

    private Set<String> referencedObjects() {
        Set<String> keys = new HashSet<>();
        collectJsonKeys(keys, travelNoteMapper.selectReferencedImageJson(), "images_json");
        collectJsonKeys(keys, travelNoteMapper.selectReferencedVideoJson(), "videos_json");
        for (String content : travelNoteMapper.selectReferencedContent()) {
            if (content == null || content.isBlank()) {
                continue;
            }
            Matcher matcher = OBJECT_REFERENCE.matcher(content);
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }

    private void collectJsonKeys(Set<String> keys, List<String> jsonValues, String column) {
        for (String json : jsonValues) {
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                keys.addAll(objectMapper.readValue(json, new TypeReference<List<String>>() {
                }));
            } catch (Exception ex) {
                log.warn("failed to parse travel_note.{}", column, ex);
            }
        }
    }
}
