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
public class BochaSearchProvider implements SearchProvider {

    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    public BochaSearchProvider(ConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GuideLink> searchGuides(GuideSearchQuery query) {
        String apiKey = configService.get(ConfigKeys.SEARCH_API_KEY, "");
        if (apiKey == null || apiKey.isBlank()) {
            throw new SearchException("博查 API Key 未配置");
        }
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(3));
            factory.setReadTimeout(Duration.ofSeconds(5));
            RestClient client = RestClient.builder().requestFactory(factory).build();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", buildQuery(query));
            body.put("count", 5);
            body.put("summary", true);
            String raw = client.post()
                    .uri("https://api.bochaai.com/v1/web-search")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode values = objectMapper.readTree(raw)
                    .path("data").path("webPages").path("value");
            List<GuideLink> links = new ArrayList<>();
            for (JsonNode value : values) {
                links.add(new GuideLink(
                        value.path("name").asText(),
                        value.path("url").asText(),
                        "bocha"));
            }
            return links;
        } catch (Exception ex) {
            throw new SearchException("博查搜索失败", ex);
        }
    }

    private String buildQuery(GuideSearchQuery query) {
        String cities = query.cityNames() == null ? "" : String.join(" ", query.cityNames());
        return query.countryName() + " " + cities + " " + query.days() + "天 旅行攻略 "
                + (query.normalizedPreference() == null ? "" : query.normalizedPreference());
    }

    @Override
    public String providerName() {
        return "bocha";
    }
}
