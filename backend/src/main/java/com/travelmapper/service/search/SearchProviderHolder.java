package com.travelmapper.service.search;

import com.travelmapper.common.config.AppProperties;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import com.travelmapper.service.config.ReloadableProvider;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SearchProviderHolder implements ReloadableProvider {

    private final ConfigService configService;
    private final AppProperties properties;
    private final Map<String, SearchProvider> providers = new LinkedHashMap<>();
    private volatile SearchProvider current = new NoopSearchProvider();
    private volatile TimeoutSearchProvider previous;

    public SearchProviderHolder(ConfigService configService, AppProperties properties,
                                List<SearchProvider> providerBeans) {
        this.configService = configService;
        this.properties = properties;
        for (SearchProvider provider : providerBeans) {
            providers.put(provider.providerName(), provider);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            reload();
        } catch (RuntimeException ex) {
            log.warn("search provider unavailable at startup: {}", ex.getMessage());
            current = new NoopSearchProvider();
        }
    }

    @Override
    public synchronized void reload() {
        String selected = configService.get(ConfigKeys.SEARCH_PROVIDER, "tavily");
        SearchProvider delegate;
        if (providers.isEmpty()) {
            delegate = new NoopSearchProvider();
        } else {
            delegate = providers.get(selected);
            if (delegate == null) {
                log.warn("unknown search provider {}; keeping previous provider {}", selected,
                        current.providerName());
                return;
            }
        }
        int readTimeout = configService.getInt(ConfigKeys.SEARCH_TIMEOUT_SECONDS,
                properties.getSearch().getReadTimeoutSeconds());
        TimeoutSearchProvider next = new TimeoutSearchProvider(delegate, readTimeout);
        TimeoutSearchProvider old = previous;
        previous = next;
        current = next;
        if (old != null) {
            old.close();
        }
    }

    public List<GuideLink> search(GuideSearchQuery query) {
        long started = System.nanoTime();
        try {
            List<GuideLink> result = current.searchGuides(query);
            log.info("search completed provider={} results={} elapsedMs={}",
                    current.providerName(), result.size(), (System.nanoTime() - started) / 1_000_000);
            return result;
        } catch (SearchTimeoutException ex) {
            log.warn("search timeout provider={} elapsedMs={}",
                    current.providerName(), (System.nanoTime() - started) / 1_000_000);
            throw ex;
        } catch (SearchException ex) {
            log.warn("search failed provider={} elapsedMs={}",
                    current.providerName(), (System.nanoTime() - started) / 1_000_000);
            throw ex;
        }
    }

    public String providerName() {
        return current.providerName();
    }

    public boolean supports(String provider) {
        return providers.containsKey(provider);
    }

    @PreDestroy
    public void shutdown() {
        if (current instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ex) {
                log.debug("failed to close search provider", ex);
            }
        }
    }
}
