package com.travelmapper.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Security security = new Security();
    private Crypto crypto = new Crypto();
    private Storage storage = new Storage();
    private Search search = new Search();
    private RateLimit rateLimit = new RateLimit();
    private Mail mail = new Mail();

    @Data
    public static class Security {
        /**
         * Temporary development-only identity stub. JWT is authoritative when an
         * Authorization header is present. Must be false in production.
         */
        private boolean adminStubEnabled = true;
        private String jwtSecret = "";
        private long jwtTtlSeconds = 604800;
    }

    @Data
    public static class Mail {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 25;
        private String username = "";
        private String password = "";
        private String from = "no-reply@travelmapper.local";
        private boolean smtpAuth = false;
        private boolean smtpStarttls = false;
    }

    @Data
    public static class Crypto {
        /**
         * Base64-encoded 32-byte AES key. Missing or invalid values fail startup.
         */
        private String aesKey = "";
    }

    @Data
    public static class Storage {
        private long uploadTtlSeconds = 300;
        private long downloadTtlSeconds = 600;
        private long shareMaxTtlSeconds = 3600;
        private String orphanCleanupCron = "0 30 3 * * *";
    }

    @Data
    public static class Search {
        private int connectTimeoutSeconds = 3;
        private int readTimeoutSeconds = 5;
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
    }
}
