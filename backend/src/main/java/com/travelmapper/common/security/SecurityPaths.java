package com.travelmapper.common.security;

public final class SecurityPaths {

    private SecurityPaths() {
    }

    public static boolean isPublic(String path, boolean devStubEnabled) {
        if (path.startsWith("/api/auth/")
                && !path.equals("/api/auth/logout")
                && !path.equals("/api/auth/logout-all")) {
            return true;
        }
        if (path.startsWith("/s/")) {
            return true;
        }
        if (path.startsWith("/actuator/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/error")) {
            return true;
        }
        return devStubEnabled && path.startsWith("/test/");
    }

    public static boolean requiresAuth(String path, boolean devStubEnabled) {
        return path.startsWith("/api/")
                && !path.startsWith("/api/admin/")
                && !isPublic(path, devStubEnabled);
    }
}
