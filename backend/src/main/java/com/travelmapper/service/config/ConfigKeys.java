package com.travelmapper.service.config;

public final class ConfigKeys {

    public static final String LLM_PROVIDER = "llm.provider";
    public static final String LLM_BASE_URL = "llm.base-url";
    public static final String LLM_MODEL = "llm.model";
    public static final String LLM_API_KEY = "llm.api-key";
    public static final String LLM_TIMEOUT_SECONDS = "llm.timeout-seconds";
    public static final String LLM_MAX_RETRY = "llm.max-retry";

    public static final String SEARCH_PROVIDER = "search.provider";
    public static final String SEARCH_API_KEY = "search.api-key";
    public static final String SEARCH_DAILY_LIMIT = "search.daily-limit";
    public static final String SEARCH_TIMEOUT_SECONDS = "search.timeout-seconds";
    public static final String SEARCH_CACHE_HOURS = "search.cache-hours";
    public static final String SEARCH_DOMAIN_WHITELIST = "search.domain-whitelist";

    public static final String STORAGE_PROVIDER = "storage.provider";
    public static final String STORAGE_ENDPOINT = "storage.endpoint";
    public static final String STORAGE_REGION = "storage.region";
    public static final String STORAGE_BUCKET = "storage.bucket";
    public static final String STORAGE_ACCESS_KEY = "storage.access-key";
    public static final String STORAGE_SECRET_KEY = "storage.secret-key";
    public static final String STORAGE_PATH_STYLE = "storage.path-style";
    public static final String STORAGE_PUBLIC_DOMAIN = "storage.public-domain";

    public static final String QUOTA_FREE_LIMIT = "quota.free-limit";
    public static final String QUOTA_VIP_DAILY = "quota.vip-daily";
    public static final String QUOTA_PACK_VALID_DAYS = "quota.pack-valid-days";
    public static final String SHARE_DEFAULT_DAYS = "share.default-days";

    private ConfigKeys() {
    }
}
