package com.rudder.server.auth;

/**
 * 请求上下文中的当前登录用户（由拦截器注入）。
 */
public record AuthPrincipal(Long userId, String email, String tokenType, Long workspaceId) {
}
