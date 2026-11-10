package com.travelmapper.service.note;

import com.travelmapper.service.config.ConfigService;
import org.springframework.stereotype.Component;

@Component
public class SensitiveWordFilter {

    private final ConfigService configService;

    public SensitiveWordFilter(ConfigService configService) {
        this.configService = configService;
    }

    public String filter(String text) {
        return filter(text, configService.get("sensitive.words", ""));
    }

    public static String filter(String text, String wordsCsv) {
        if (text == null || text.isEmpty() || wordsCsv == null || wordsCsv.isBlank()) {
            return text;
        }
        String result = text;
        for (String word : wordsCsv.split(",")) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                result = result.replace(trimmed, "***");
            }
        }
        return result;
    }
}
