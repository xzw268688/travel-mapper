package com.travelmapper.service.expense;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.config.AppProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class OpenExchangeRateProvider implements ExchangeRateProvider {

    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public OpenExchangeRateProvider(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Map<String, BigDecimal> latestToCny(Set<String> currencies) {
        if (currencies == null || currencies.isEmpty()) {
            return Map.of();
        }
        AppProperties.ExchangeRate config = properties.getExchangeRate();
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(config.getConnectTimeoutSeconds()));
            factory.setReadTimeout(Duration.ofSeconds(config.getReadTimeoutSeconds()));
            RestClient client = RestClient.builder().requestFactory(factory).build();
            String raw = client.get()
                    .uri(config.getApiUrl())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String status = root.path("result").asText();
            if (!status.isBlank() && !"success".equalsIgnoreCase(status)) {
                throw new IllegalStateException("汇率数据源返回失败");
            }
            String base = root.path("base_code").asText(root.path("base").asText());
            if (!"CNY".equalsIgnoreCase(base)) {
                throw new IllegalStateException("汇率数据源基准币种不是 CNY");
            }
            JsonNode rates = root.path("rates");
            if (rates.isMissingNode() || !rates.isObject() || rates.isEmpty()) {
                throw new IllegalStateException("汇率数据源未返回汇率");
            }
            Map<String, BigDecimal> result = new HashMap<>();
            for (String currency : currencies) {
                JsonNode perCny = rates.get(currency);
                if (perCny == null || !perCny.isNumber()) {
                    continue;
                }
                BigDecimal value = perCny.decimalValue();
                if (value.signum() <= 0) {
                    continue;
                }
                result.put(currency, BigDecimal.ONE.divide(value, 10, RoundingMode.HALF_UP));
            }
            return result;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("无法从汇率数据源获取数据", ex);
        }
    }

    @Override
    public String source() {
        return "exchangerate-api.com";
    }
}