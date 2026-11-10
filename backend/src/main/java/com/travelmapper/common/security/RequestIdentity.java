package com.travelmapper.common.security;

public record RequestIdentity(Long userId, String role) {

    public static final String ATTRIBUTE = RequestIdentity.class.getName();
    public static final RequestIdentity ANONYMOUS = new RequestIdentity(null, "ANONYMOUS");

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isAuthenticated() {
        return userId != null;
    }
}
