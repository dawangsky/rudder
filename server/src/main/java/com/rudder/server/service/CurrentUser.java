package com.rudder.server.service;

import com.rudder.server.auth.AuthContext;
import com.rudder.server.auth.AuthPrincipal;

/** 从鉴权上下文读取当前工作区 / 用户。 */
public final class CurrentUser {
    private CurrentUser() {
    }

    public static AuthPrincipal require() {
        AuthPrincipal p = AuthContext.get();
        if (p == null) {
            throw new IllegalArgumentException("未登录");
        }
        return p;
    }
}
