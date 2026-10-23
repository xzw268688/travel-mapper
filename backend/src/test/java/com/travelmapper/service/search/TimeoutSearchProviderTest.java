package com.travelmapper.service.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeoutSearchProviderTest {

    private final GuideSearchQuery query = new GuideSearchQuery(
            "JP", "日本", List.of("TYO"), List.of("东京"), 3, "food");

    @Test
    void returnsFastResult() {
        TimeoutSearchProvider provider = new TimeoutSearchProvider(new FastProvider(), 2);
        assertThat(provider.searchGuides(query)).hasSize(1);
        provider.close();
    }

    @Test
    void slowProviderTimesOut() {
        TimeoutSearchProvider provider = new TimeoutSearchProvider(new SlowProvider(), 1);
        assertThatThrownBy(() -> provider.searchGuides(query))
                .isInstanceOf(SearchTimeoutException.class);
        provider.close();
    }

    @Test
    void providerFailureIsWrapped() {
        TimeoutSearchProvider provider = new TimeoutSearchProvider(new BrokenProvider(), 2);
        assertThatThrownBy(() -> provider.searchGuides(query))
                .isInstanceOf(SearchException.class);
        provider.close();
    }

    private static final class FastProvider implements SearchProvider {
        @Override
        public List<GuideLink> searchGuides(GuideSearchQuery query) {
            return List.of(new GuideLink("guide", "https://example.com", "example.com"));
        }

        @Override
        public String providerName() {
            return "fast";
        }
    }

    private static final class SlowProvider implements SearchProvider {
        @Override
        public List<GuideLink> searchGuides(GuideSearchQuery query) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }

        @Override
        public String providerName() {
            return "slow";
        }
    }

    private static final class BrokenProvider implements SearchProvider {
        @Override
        public List<GuideLink> searchGuides(GuideSearchQuery query) {
            throw new IllegalStateException("provider down");
        }

        @Override
        public String providerName() {
            return "broken";
        }
    }
}
