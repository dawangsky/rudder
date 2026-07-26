package com.rudder.server.web;

import com.rudder.server.auth.AuthContext;
import com.rudder.server.auth.AuthPrincipal;
import com.rudder.server.domain.TokenTypes;
import com.rudder.server.service.AuthService;
import com.rudder.server.web.dto.LoginRequest;
import com.rudder.server.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证 API：注册 / 会话登录 / Daemon 登录 / 当前用户。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.getEmail(), request.getPassword(), request.getDisplayName());
    }

    /** Desktop / Web 会话登录，返回 sessionToken。 */
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return authService.loginSession(request.getEmail(), request.getPassword());
    }

    /** CLI Daemon 登录，返回与 session 分离的 daemonToken。 */
    @PostMapping("/daemon-login")
    public Map<String, Object> daemonLogin(@Valid @RequestBody LoginRequest request) {
        return authService.loginDaemon(request.getEmail(), request.getPassword());
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || !TokenTypes.SESSION.equals(principal.tokenType())) {
            throw new IllegalArgumentException("未登录或 Token 类型不正确");
        }
        return authService.me(principal);
    }

    /** 引导页：创建工作区（无工作区时也可调用）。 */
    @PostMapping("/workspaces")
    public Map<String, Object> createWorkspace(@RequestBody Map<String, Object> body) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || !TokenTypes.SESSION.equals(principal.tokenType())) {
            throw new IllegalArgumentException("需要会话登录");
        }
        return authService.createWorkspace(
                principal,
                body.get("name") == null ? "" : String.valueOf(body.get("name")),
                body.get("slug") == null ? null : String.valueOf(body.get("slug")),
                body.get("issuePrefix") == null ? null : String.valueOf(body.get("issuePrefix"))
        );
    }
}
