package com.rudder.server.auth;

import com.rudder.server.domain.TokenTypes;
import com.rudder.server.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Bearer Token 鉴权拦截器：校验 session 或 daemon Token。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 浏览器 CORS 预检（OPTIONS）无 Authorization，必须放行，否则前端会报跨域失败
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        String raw = header.substring("Bearer ".length()).trim();
        // 受保护接口默认接受 session；Daemon 专用接口可在后续按路径收紧
        AuthPrincipal principal = authService.authenticateBearer(raw, null);
        if (principal == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        // /api/auth/me、创建工作区：允许尚无工作区的会话
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/me") && !TokenTypes.SESSION.equals(principal.tokenType())) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        boolean workspaceExempt =
                path.startsWith("/api/auth/me")
                        || path.startsWith("/api/auth/onboarding-profile")
                        || path.startsWith("/api/auth/workspaces");
        if (!workspaceExempt && principal.workspaceId() == null && path.startsWith("/api/")) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }
        AuthContext.set(principal);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
}
