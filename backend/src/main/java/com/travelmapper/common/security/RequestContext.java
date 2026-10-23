package com.travelmapper.common.security;

import org.slf4j.MDC;

public final class RequestContext {

    private static final ThreadLocal<RequestIdentity> HOLDER = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void set(RequestIdentity identity) {
        HOLDER.set(identity == null ? RequestIdentity.ANONYMOUS : identity);
    }

    public static RequestIdentity get() {
        RequestIdentity identity = HOLDER.get();
        return identity == null ? RequestIdentity.ANONYMOUS : identity;
    }

    public static Long userIdOrNull() {
        return get().userId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static String traceId() {
        return MDC.get("traceId");
    }
}
