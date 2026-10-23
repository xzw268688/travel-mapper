package com.travelmapper.service.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TavilySearchProvider implements SearchProvider {

    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    public TavilySearchProvider(ConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GuideLink> searchGuides(GuideSearchQuery query) {
        String apiKey = configService.get(ConfigKeys.SEARCH_API_KEY, "");
        if (apiKey == null || apiKey.isBlank()) {
            throw new SearchException("Tavily API Key 未配置");
        }
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(3));
            factory.setReadTimeout(Duration.ofSeconds(5));
            RestClient client = RestClient.builder().requestFactory(factory).build();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("api_key", apiKey);
            body.put("query", buildQuery(query));
            body.put("max_results", 5);
            body.put("search_depth", "basic");
            String raw = client.post()
                    .uri("https://api.tavily.com/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode results = objectMapper.readTree(raw).path("results");
            List<GuideLink> links = new ArrayList<>();
            for (JsonNode result : results) {
                links.add(new GuideLink(
                        result.path("title").asText(),
                        result.path("url").asText(),
                        "tavily"));
            }
            return links;
        } catch (Exception ex) {
            throw new SearchException("Tavily 搜索失败", ex);
        }
    }

    private String buildQuery(GuideSearchQuery query) {
        String cities = query.cityNames() == null ? "" : String.join(" ", query.cityNames());
        return query.countryName() + " " + cities + " " + query.days() + "天 旅行攻略 "
                + (query.normalizedPreference() == null ? "" : query.normalizedPreference());
    }

    @Override
    public String providerName() {
        return "tavily";
    }
}
