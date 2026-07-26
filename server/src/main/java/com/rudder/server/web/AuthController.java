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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认证 API：注册 / 会话登录 / Daemon 登录 / 当前用户 / 工作区。
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
        return authService.me(requireSession());
    }

    /** 引导：保存角色 / 使用目的到 rb_user。 */
    @PostMapping("/onboarding-profile")
    public Map<String, Object> onboardingProfile(@RequestBody Map<String, Object> body) {
        return authService.saveOnboardingProfile(
                requireSession(),
                str(body.get("role")),
                str(body.get("intent"))
        );
    }

    /** 列出当前账号加入的全部工作区。 */
    @GetMapping("/workspaces")
    public List<Map<String, Object>> workspaces() {
        return authService.listWorkspaces(requireSession());
    }

    /** 创建工作区（可多次，一账号多工作区）。 */
    @PostMapping("/workspaces")
    public Map<String, Object> createWorkspace(@RequestBody Map<String, Object> body) {
        return authService.createWorkspace(
                requireSession(),
                str(body.get("name")),
                emptyToNull(str(body.get("slug"))),
                emptyToNull(str(body.get("issuePrefix"))),
                emptyToNull(str(body.get("role"))),
                emptyToNull(str(body.get("intent")))
        );
    }

    /** 切换当前工作区。 */
    @PostMapping("/workspaces/{id}/switch")
    public Map<String, Object> switchWorkspace(@PathVariable Long id) {
        return authService.switchWorkspace(requireSession(), id);
    }

    private AuthPrincipal requireSession() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || !TokenTypes.SESSION.equals(principal.tokenType())) {
            throw new IllegalArgumentException("需要会话登录");
        }
        return principal;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
