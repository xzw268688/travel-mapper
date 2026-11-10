package com.travelmapper.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import com.travelmapper.service.config.ReloadableProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LlmProviderRegistry implements ReloadableProvider {

    private final ConfigService configService;
    private final List<LlmProvider> providerBeans;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;
    private volatile LlmProvider current;

    public LlmProviderRegistry(ConfigService configService, List<LlmProvider> providerBeans,
                               ObjectMapper objectMapper, PromptBuilder promptBuilder) {
        this.configService = configService;
        this.providerBeans = providerBeans;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
        this.current = new HttpLlmProvider(configService, objectMapper, promptBuilder);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        reload();
    }

    @Override
    public synchronized void reload() {
        String selected = configService.get(ConfigKeys.LLM_PROVIDER, "qwen");
        LlmProvider bean = providerBeans.stream()
                .filter(provider -> provider.providerName().equals(selected))
                .findFirst()
                .orElse(null);
        current = bean != null ? bean
                : new HttpLlmProvider(configService, objectMapper, promptBuilder);
    }

    public LlmProvider get() {
        return current;
    }
}
