package com.travelmapper.support;

import com.travelmapper.service.config.ConfigEntry;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.llm.LlmProviderRegistry;
import com.travelmapper.service.search.SearchProvider;
import com.travelmapper.service.search.SearchProviderHolder;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.Set;

@Import(AiTestSupport.AiTestConfig.class)
public abstract class AiTestSupport extends AuthTestSupport {

    @TestConfiguration
    static class AiTestConfig {
        @Bean
        FakeLlmProvider fakeLlmProvider() {
            return new FakeLlmProvider();
        }

        @Bean
        SearchProvider fakeSearchProvider() {
            return new FakeSearchProvider();
        }
    }

    @Autowired
    protected FakeLlmProvider fakeLlm;

    @Autowired
    protected FakeSearchProvider fakeSearch;

    @Autowired
    protected LlmProviderRegistry llmProviderRegistry;

    @Autowired
    protected SearchProviderHolder searchProviderHolder;

    @BeforeEach
    void configureAiProviders() {
        fakeLlm.reset();
        fakeSearch.reset();
        clearKeys("guides:*");
        clearKeys("prep:*");
        clearKeys("country-guide:*");
        clearKeys("quota:dedupe:*");
        clearKeys("quota:dedupe:lock:*");
        configService.updateGroup(Map.of(
                ConfigKeys.LLM_PROVIDER, ConfigEntry.plain("fake", "test"),
                ConfigKeys.SEARCH_PROVIDER, ConfigEntry.plain("fake", "test"),
                ConfigKeys.SEARCH_TIMEOUT_SECONDS, ConfigEntry.plain("5", "test"),
                ConfigKeys.SEARCH_DAILY_LIMIT, ConfigEntry.plain("50", "test")), 1L);
        llmProviderRegistry.reload();
        searchProviderHolder.reload();
    }

    protected void clearKeys(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
