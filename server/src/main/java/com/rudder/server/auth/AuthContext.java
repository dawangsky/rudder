package com.rudder.server.auth;

/**
 * 基于 ThreadLocal 的请求级鉴权上下文。
 */
public final class AuthContext {
    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        HOLDER.set(principal);
    }

    public static AuthPrincipal get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
