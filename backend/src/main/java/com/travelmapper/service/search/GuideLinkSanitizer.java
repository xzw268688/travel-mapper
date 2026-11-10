package com.travelmapper.service.search;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class GuideLinkSanitizer {

    public static final int MAX_LINKS = 3;
    public static final int MAX_URL_LENGTH = 500;

    public List<GuideLink> sanitize(List<GuideLink> links) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        Map<String, GuideLink> unique = new LinkedHashMap<>();
        for (GuideLink link : links) {
            if (link == null || !isValidUrl(link.url())) {
                continue;
            }
            unique.putIfAbsent(link.url(), link);
            if (unique.size() >= MAX_LINKS) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    public boolean isValidUrl(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return scheme != null
                    && ("http".equals(scheme.toLowerCase(Locale.ROOT))
                    || "https".equals(scheme.toLowerCase(Locale.ROOT)))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
