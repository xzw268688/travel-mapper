package com.travelmapper.service.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideLinkSanitizerTest {

    private final GuideLinkSanitizer sanitizer = new GuideLinkSanitizer();

    @Test
    void keepsOnlyHttpAndHttps() {
        List<GuideLink> result = sanitizer.sanitize(List.of(
                new GuideLink("ok", "https://example.com/a", "example.com"),
                new GuideLink("js", "javascript:alert(1)", "x"),
                new GuideLink("ftp", "ftp://example.com", "x"),
                new GuideLink("plain", "http://example.com/b", "example.com")));
        assertThat(result).extracting(GuideLink::url)
                .containsExactly("https://example.com/a", "http://example.com/b");
    }

    @Test
    void removesDuplicatesAndLimitsToThree() {
        List<GuideLink> result = sanitizer.sanitize(List.of(
                new GuideLink("a", "https://example.com/1", "x"),
                new GuideLink("a2", "https://example.com/1", "x"),
                new GuideLink("b", "https://example.com/2", "x"),
                new GuideLink("c", "https://example.com/3", "x"),
                new GuideLink("d", "https://example.com/4", "x")));
        assertThat(result).hasSize(3);
        assertThat(result).extracting(GuideLink::url)
                .containsExactly("https://example.com/1", "https://example.com/2", "https://example.com/3");
    }

    @Test
    void rejectsOverlongAndMalformedUrls() {
        assertThat(sanitizer.isValidUrl("https://example.com/" + "a".repeat(500))).isFalse();
        assertThat(sanitizer.isValidUrl("not a url")).isFalse();
        assertThat(sanitizer.isValidUrl("https://")).isFalse();
    }
}
