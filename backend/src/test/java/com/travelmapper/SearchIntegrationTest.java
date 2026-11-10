package com.travelmapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.travelmapper.service.config.ConfigEntry;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import com.travelmapper.service.search.GuideLink;
import com.travelmapper.service.search.GuideSearchQuery;
import com.travelmapper.service.search.SearchProvider;
import com.travelmapper.service.search.SearchProviderHolder;
import com.travelmapper.service.search.SearchTimeoutException;
import com.travelmapper.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(SearchIntegrationTest.FakeSearchConfig.class)
class SearchIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class FakeSearchConfig {
        @Bean
        SearchProvider fakeSearchProvider() {
            return new FakeSearchProvider();
        }
    }

    static class FakeSearchProvider implements SearchProvider {
        @Override
        public List<GuideLink> searchGuides(GuideSearchQuery query) {
            if (query.normalizedPreference() != null && query.normalizedPreference().contains("slow")) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return List.of(new GuideLink("参考攻略", "https://example.com/guide", "example.com"));
        }

        @Override
        public String providerName() {
            return "fake";
        }
    }

    @Autowired
    private ConfigService configService;

    @Autowired
    private SearchProviderHolder searchProviderHolder;

    private final GuideSearchQuery query = new GuideSearchQuery(
            "JP", "日本", List.of("TYO"), List.of("东京"), 3, "food");

    @BeforeEach
    void configureSearch() {
        setSearchConfig("fake", 5, "sk-search-secret");
    }

    @Test
    void configuredProviderIsSelectedAndReturnsLinks() {
        assertThat(searchProviderHolder.providerName()).isEqualTo("fake");
        assertThat(searchProviderHolder.search(query)).hasSize(1);
    }

    @Test
    void unknownProviderIsRejectedAndOldProviderKept() throws Exception {
        mockMvc.perform(asAdmin(put("/api/admin/config/search"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"missing\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        assertThat(searchProviderHolder.providerName()).isEqualTo("fake");
        mockMvc.perform(asAdmin(get("/api/admin/config/search")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("fake"));
    }

    @Test
    void slowProviderTimesOutAtConfiguredBoundary() {
        setSearchConfig("fake", 1, "sk-search-secret");
        GuideSearchQuery slowQuery = new GuideSearchQuery(
                "JP", "日本", List.of("TYO"), List.of("东京"), 3, "slow");
        assertThatThrownBy(() -> searchProviderHolder.search(slowQuery))
                .isInstanceOf(SearchTimeoutException.class);
    }

    @Test
    void searchLogsDoNotContainSecrets() {
        Logger logger = (Logger) LoggerFactory.getLogger(SearchProviderHolder.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            searchProviderHolder.search(query);
        } finally {
            logger.detachAppender(appender);
        }
        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(logs).contains("provider=fake");
        assertThat(logs).doesNotContain("sk-search-secret");
        assertThat(logs).doesNotContain("Authorization");
    }

    private void setSearchConfig(String provider, int timeoutSeconds, String apiKey) {
        Map<String, ConfigEntry> updates = new LinkedHashMap<>();
        updates.put(ConfigKeys.SEARCH_PROVIDER, ConfigEntry.plain(provider, "搜索供应商"));
        updates.put(ConfigKeys.SEARCH_TIMEOUT_SECONDS, ConfigEntry.plain(String.valueOf(timeoutSeconds), "搜索独立超时"));
        updates.put(ConfigKeys.SEARCH_API_KEY, ConfigEntry.secret(apiKey, "AES 加密存储"));
        configService.updateGroup(updates, 1L);
        searchProviderHolder.reload();
    }
}
