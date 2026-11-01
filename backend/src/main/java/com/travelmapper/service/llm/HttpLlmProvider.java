package com.travelmapper.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * OpenAI-compatible chat-completions adapter. Kept behind LlmProvider so a
 * LangChain4j implementation can replace it without touching business code.
 */
public class HttpLlmProvider implements LlmProvider {

    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;

    public HttpLlmProvider(ConfigService configService, ObjectMapper objectMapper,
                           PromptBuilder promptBuilder) {
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public String providerName() {
        return configService.get(ConfigKeys.LLM_PROVIDER, "qwen");
    }

    @Override
    public String generateItinerary(LlmItineraryRequest request) {
        String baseUrl = configService.get(ConfigKeys.LLM_BASE_URL, "");
        String model = configService.get(ConfigKeys.LLM_MODEL, "");
        String apiKey = configService.get(ConfigKeys.LLM_API_KEY, "");
        int timeout = configService.getInt(ConfigKeys.LLM_TIMEOUT_SECONDS, 30);
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "LLM API Key 未配置");
        }
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(3));
            factory.setReadTimeout(Duration.ofSeconds(timeout));
            RestClient client = RestClient.builder()
                    .baseUrl(stripTrailingSlash(baseUrl))
                    .requestFactory(factory)
                    .build();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user",
                    "content", promptBuilder.buildItineraryPrompt(request))));
            // 不显式设置 temperature：部分供应商（如火山方舟的 Kimi）只接受固定值，
            // 省略后由供应商使用各自默认值。
            body.put("response_format", Map.of("type", "json_object"));
            String raw = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
            }
            return content;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "LLM 调用失败");
        }
    }

    @Override
    public void polishStream(String text, String style, String target,
                             Consumer<String> onDelta, BooleanSupplier cancelled) {
        String baseUrl = configService.get(ConfigKeys.LLM_BASE_URL, "");
        String model = configService.get(ConfigKeys.LLM_MODEL, "");
        String apiKey = configService.get(ConfigKeys.LLM_API_KEY, "");
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "LLM API Key 未配置");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user",
                    "content", promptBuilder.buildPolishPrompt(text, style, target))));
            // 同上：交由供应商决定默认温度。
            body.put("stream", true);
            String json = objectMapper.writeValueAsString(body);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(stripTrailingSlash(baseUrl) + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<Stream<String>> response = client.send(request,
                    HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
            }
            for (String line : (Iterable<String>) response.body()::iterator) {
                if (cancelled.getAsBoolean()) {
                    return;
                }
                if (line == null || !line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    return;
                }
                JsonNode node = objectMapper.readTree(data);
                String delta = node.path("choices").path(0).path("delta").path("content").asText();
                if (delta != null && !delta.isEmpty()) {
                    onDelta.accept(delta);
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "LLM 流式调用失败");
        }
    }

    private String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
