package com.travelmapper.service.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 从 Spring MVC 映射生成 OpenAPI 兼容的接口清单，供后台页面渲染。
 * 不依赖 springdoc 的分组资源，避免测试环境与分组配置差异。
 */
@Service
public class AdminApiDocsService {

    private static final long CACHE_TTL_MS = 60_000;

    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectMapper objectMapper;
    private final boolean swaggerUiEnabled;
    private volatile CachedDocs cached;

    public AdminApiDocsService(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            ObjectMapper objectMapper,
            @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerUiEnabled) {
        this.handlerMapping = handlerMapping;
        this.objectMapper = objectMapper;
        this.swaggerUiEnabled = swaggerUiEnabled;
    }

    public JsonNode apiDocs() {
        CachedDocs current = cached;
        if (current != null && !current.expired()) {
            return current.node();
        }
        synchronized (this) {
            current = cached;
            if (current != null && !current.expired()) {
                return current.node();
            }
            Map<String, Object> paths = new TreeMap<>();
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                    : handlerMapping.getHandlerMethods().entrySet()) {
                RequestMappingInfo info = entry.getKey();
                HandlerMethod handler = entry.getValue();
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                List<RequestMethod> effective = methods.isEmpty()
                        ? List.of(RequestMethod.GET) : List.copyOf(methods);
                String tag = tagOf(handler);
                String summary = summaryOf(handler);
                for (String pattern : info.getPatternValues()) {
                    if (!pattern.startsWith("/api/") && !pattern.startsWith("/s/")) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> operations = (Map<String, Object>) paths
                            .computeIfAbsent(pattern, key -> new LinkedHashMap<>());
                    for (RequestMethod method : effective) {
                        Map<String, Object> operation = new LinkedHashMap<>();
                        operation.put("tags", List.of(tag));
                        operation.put("summary", summary);
                        operations.put(method.name().toLowerCase(Locale.ROOT), operation);
                    }
                }
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("openapi", "3.0.1");
            root.put("info", Map.of("title", "TravelMapper API", "version", "1.0"));
            root.put("x-swagger-ui-enabled", swaggerUiEnabled);
            root.put("paths", paths);
            JsonNode node = objectMapper.valueToTree(root);
            cached = new CachedDocs(node, System.currentTimeMillis() + CACHE_TTL_MS);
            return node;
        }
    }

    private String tagOf(HandlerMethod handler) {
        Tag tag = handler.getBeanType().getAnnotation(Tag.class);
        return tag != null && !tag.name().isBlank() ? tag.name() : "其他";
    }

    private String summaryOf(HandlerMethod handler) {
        Operation operation = handler.getMethodAnnotation(Operation.class);
        if (operation != null && !operation.summary().isBlank()) {
            return operation.summary();
        }
        return handler.getMethod().getName();
    }

    private record CachedDocs(JsonNode node, long expireAt) {
        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}