package com.rudder.server.domain;

/**
 * Token 类型：会话（Desktop/API）与 Daemon 分离。
 */
public final class TokenTypes {
    public static final String SESSION = "session";
    public static final String DAEMON = "daemon";

    private TokenTypes() {
    }
}
