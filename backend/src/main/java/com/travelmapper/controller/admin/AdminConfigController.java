package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.audit.Auditable;
import com.travelmapper.common.crypto.AesCryptoService;
import com.travelmapper.common.security.RequestContext;
import com.travelmapper.controller.admin.dto.LlmConfigDto;
import com.travelmapper.controller.admin.dto.SearchConfigDto;
import com.travelmapper.controller.admin.dto.StorageConfigDto;
import com.travelmapper.service.config.ConfigEntry;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigReloadService;
import com.travelmapper.service.config.ConfigService;
import com.travelmapper.service.search.SearchProviderHolder;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Tag(name = "Admin Config")
@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {

    private final ConfigService configService;
    private final ConfigReloadService reloadService;
    private final SearchProviderHolder searchProviderHolder;

    public AdminConfigController(ConfigService configService, ConfigReloadService reloadService,
                                 SearchProviderHolder searchProviderHolder) {
        this.configService = configService;
        this.reloadService = reloadService;
        this.searchProviderHolder = searchProviderHolder;
    }

    @GetMapping("/llm")
    public ApiResponse<LlmConfigDto> getLlm() {
        LlmConfigDto dto = new LlmConfigDto();
        dto.setProvider(configService.get(ConfigKeys.LLM_PROVIDER));
        dto.setBaseUrl(configService.get(ConfigKeys.LLM_BASE_URL));
        dto.setModel(configService.get(ConfigKeys.LLM_MODEL));
        dto.setApiKey(AesCryptoService.mask(configService.get(ConfigKeys.LLM_API_KEY)));
        dto.setTimeoutSeconds(configService.getInt(ConfigKeys.LLM_TIMEOUT_SECONDS, 30));
        dto.setMaxRetry(configService.getInt(ConfigKeys.LLM_MAX_RETRY, 1));
        return ApiResponse.ok(dto);
    }

    @PutMapping("/llm")
    @Auditable(action = "CONFIG_UPDATE", targetType = "CONFIG")
    public ApiResponse<LlmConfigDto> updateLlm(@RequestBody LlmConfigDto dto) {
        if (dto.getTimeoutSeconds() != null && (dto.getTimeoutSeconds() < 1 || dto.getTimeoutSeconds() > 120)) {
            throw invalid("llm.timeout-seconds 必须在 1 到 120 之间");
        }
        if (dto.getMaxRetry() != null && (dto.getMaxRetry() < 0 || dto.getMaxRetry() > 5)) {
            throw invalid("llm.max-retry 必须在 0 到 5 之间");
        }
        if (hasText(dto.getBaseUrl())) {
            requireHttpUrl(dto.getBaseUrl(), "llm.base-url");
        }

        Map<String, ConfigEntry> updates = new LinkedHashMap<>();
        putText(updates, ConfigKeys.LLM_PROVIDER, dto.getProvider(), "当前 LLM 供应商");
        putText(updates, ConfigKeys.LLM_BASE_URL, dto.getBaseUrl(), "OpenAI 兼容端点");
        putText(updates, ConfigKeys.LLM_MODEL, dto.getModel(), "模型名");
        putSecret(updates, ConfigKeys.LLM_API_KEY, dto.getApiKey(), "AES 加密存储");
        putNumber(updates, ConfigKeys.LLM_TIMEOUT_SECONDS, dto.getTimeoutSeconds(), "单次调用超时");
        putNumber(updates, ConfigKeys.LLM_MAX_RETRY, dto.getMaxRetry(), "JSON 解析失败重试次数");
        apply(updates);
        return getLlm();
    }

    @GetMapping("/oss")
    public ApiResponse<StorageConfigDto> getStorage() {
        StorageConfigDto dto = new StorageConfigDto();
        dto.setProvider(configService.get(ConfigKeys.STORAGE_PROVIDER));
        dto.setEndpoint(configService.get(ConfigKeys.STORAGE_ENDPOINT));
        dto.setRegion(configService.get(ConfigKeys.STORAGE_REGION));
        dto.setBucket(configService.get(ConfigKeys.STORAGE_BUCKET));
        dto.setAccessKey(AesCryptoService.mask(configService.get(ConfigKeys.STORAGE_ACCESS_KEY)));
        dto.setSecretKey(AesCryptoService.mask(configService.get(ConfigKeys.STORAGE_SECRET_KEY)));
        dto.setPathStyle(configService.getBoolean(ConfigKeys.STORAGE_PATH_STYLE, true));
        dto.setPublicDomain(configService.get(ConfigKeys.STORAGE_PUBLIC_DOMAIN));
        return ApiResponse.ok(dto);
    }

    @PutMapping("/oss")
    @Auditable(action = "CONFIG_UPDATE", targetType = "CONFIG")
    public ApiResponse<StorageConfigDto> updateStorage(@RequestBody StorageConfigDto dto) {
        if (hasText(dto.getEndpoint())) {
            requireHttpUrl(dto.getEndpoint(), "storage.endpoint");
        }
        Map<String, ConfigEntry> updates = new LinkedHashMap<>();
        putText(updates, ConfigKeys.STORAGE_PROVIDER, dto.getProvider(), "oss / minio");
        putText(updates, ConfigKeys.STORAGE_ENDPOINT, dto.getEndpoint(), "服务端点");
        putText(updates, ConfigKeys.STORAGE_REGION, dto.getRegion(), "MinIO 可空");
        putText(updates, ConfigKeys.STORAGE_BUCKET, dto.getBucket(), "桶名");
        putSecret(updates, ConfigKeys.STORAGE_ACCESS_KEY, dto.getAccessKey(), "AES 加密存储");
        putSecret(updates, ConfigKeys.STORAGE_SECRET_KEY, dto.getSecretKey(), "AES 加密存储");
        if (dto.getPathStyle() != null) {
            updates.put(ConfigKeys.STORAGE_PATH_STYLE,
                    ConfigEntry.plain(String.valueOf(dto.getPathStyle()), "MinIO=true，云 OSS=false"));
        }
        putText(updates, ConfigKeys.STORAGE_PUBLIC_DOMAIN, dto.getPublicDomain(), "可选 CDN 域名");
        apply(updates);
        return getStorage();
    }

    @GetMapping("/search")
    public ApiResponse<SearchConfigDto> getSearch() {
        SearchConfigDto dto = new SearchConfigDto();
        dto.setProvider(configService.get(ConfigKeys.SEARCH_PROVIDER));
        dto.setApiKey(AesCryptoService.mask(configService.get(ConfigKeys.SEARCH_API_KEY)));
        dto.setDailyLimit(configService.getInt(ConfigKeys.SEARCH_DAILY_LIMIT, 50));
        dto.setTimeoutSeconds(configService.getInt(ConfigKeys.SEARCH_TIMEOUT_SECONDS, 5));
        dto.setCacheHours(configService.getInt(ConfigKeys.SEARCH_CACHE_HOURS, 24));
        dto.setDomainWhitelist(configService.get(ConfigKeys.SEARCH_DOMAIN_WHITELIST));
        return ApiResponse.ok(dto);
    }

    @PutMapping("/search")
    @Auditable(action = "CONFIG_UPDATE", targetType = "CONFIG")
    public ApiResponse<SearchConfigDto> updateSearch(@RequestBody SearchConfigDto dto) {
        if (dto.getDailyLimit() != null && (dto.getDailyLimit() < 1 || dto.getDailyLimit() > 1000)) {
            throw invalid("search.daily-limit 必须在 1 到 1000 之间");
        }
        if (dto.getTimeoutSeconds() != null && (dto.getTimeoutSeconds() < 1 || dto.getTimeoutSeconds() > 30)) {
            throw invalid("search.timeout-seconds 必须在 1 到 30 之间");
        }
        if (dto.getCacheHours() != null && (dto.getCacheHours() < 1 || dto.getCacheHours() > 168)) {
            throw invalid("search.cache-hours 必须在 1 到 168 之间");
        }
        if (hasText(dto.getProvider()) && !searchProviderHolder.supports(dto.getProvider())) {
            throw invalid("未知搜索供应商：" + dto.getProvider());
        }
        Map<String, ConfigEntry> updates = new LinkedHashMap<>();
        putText(updates, ConfigKeys.SEARCH_PROVIDER, dto.getProvider(), "搜索供应商");
        putSecret(updates, ConfigKeys.SEARCH_API_KEY, dto.getApiKey(), "AES 加密存储");
        putNumber(updates, ConfigKeys.SEARCH_DAILY_LIMIT, dto.getDailyLimit(), "每用户每日搜索上限");
        putNumber(updates, ConfigKeys.SEARCH_TIMEOUT_SECONDS, dto.getTimeoutSeconds(), "搜索独立超时");
        putNumber(updates, ConfigKeys.SEARCH_CACHE_HOURS, dto.getCacheHours(), "guides 缓存时长");
        putText(updates, ConfigKeys.SEARCH_DOMAIN_WHITELIST, dto.getDomainWhitelist(),
                "外链域名白名单，逗号分隔；V1 只记录不强制拦截");
        apply(updates);
        return getSearch();
    }

    private void apply(Map<String, ConfigEntry> updates) {
        if (updates.isEmpty()) {
            return;
        }
        Set<String> keys = updates.keySet();
        Map<String, ConfigEntry> before = configService.snapshot(keys);
        configService.updateGroup(updates, RequestContext.userIdOrNull());
        try {
            reloadService.reloadAll();
        } catch (RuntimeException ex) {
            configService.updateGroup(before, RequestContext.userIdOrNull());
            reloadService.reloadAllQuietly();
            throw new BusinessException(ErrorCode.PARAM_INVALID, "配置无法生效：" + ex.getMessage());
        }
    }

    private void putText(Map<String, ConfigEntry> updates, String key, String value, String remark) {
        if (value != null) {
            updates.put(key, ConfigEntry.plain(value, remark));
        }
    }

    private void putSecret(Map<String, ConfigEntry> updates, String key, String value, String remark) {
        if (value != null && !value.isBlank()) {
            updates.put(key, ConfigEntry.secret(value, remark));
        }
    }

    private void putNumber(Map<String, ConfigEntry> updates, String key, Integer value, String remark) {
        if (value != null) {
            updates.put(key, ConfigEntry.plain(String.valueOf(value), remark));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void requireHttpUrl(String value, String field) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null
                    || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
                throw invalid(field + " 必须是 http/https URL");
            }
        } catch (IllegalArgumentException ex) {
            throw invalid(field + " 必须是合法 URL");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message);
    }
}
