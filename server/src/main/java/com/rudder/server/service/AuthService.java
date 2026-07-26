package com.rudder.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rudder.server.auth.AuthPrincipal;
import com.rudder.server.auth.TokenHasher;
import com.rudder.server.domain.TokenTypes;
import com.rudder.server.domain.UserEntity;
import com.rudder.server.domain.UserTokenEntity;
import com.rudder.server.domain.WorkspaceEntity;
import com.rudder.server.domain.WorkspaceMemberEntity;
import com.rudder.server.mapper.UserMapper;
import com.rudder.server.mapper.UserTokenMapper;
import com.rudder.server.mapper.WorkspaceMapper;
import com.rudder.server.mapper.WorkspaceMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 认证与工作区：注册不自动建工作区；新用户需引导页手动创建后才能进入产品。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final UserTokenMapper userTokenMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public Map<String, Object> register(String email, String password, String displayName) {
        String normalized = normalizeEmail(email);
        UserEntity existing = findByEmail(normalized);
        if (existing != null) {
            throw new IllegalArgumentException("邮箱不可用");
        }
        if (!StringUtils.hasText(password) || password.length() < 6) {
            throw new IllegalArgumentException("密码长度需至少 6 位");
        }

        UserEntity user = new UserEntity();
        user.setEmail(normalized);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(resolveDisplayName(normalized, displayName));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        // 新用户不自动创建工作区，需引导页手动创建
        String sessionToken = issueToken(user.getId(), TokenTypes.SESSION, "desktop-session");
        return authPayload(user, null, sessionToken, null);
    }

    @Transactional
    public Map<String, Object> loginSession(String email, String password) {
        UserEntity user = requireValidUser(email, password);
        WorkspaceEntity workspace = findDefaultWorkspace(user.getId());
        String sessionToken = issueToken(user.getId(), TokenTypes.SESSION, "desktop-session");
        return authPayload(user, workspace, sessionToken, null);
    }

    /**
     * CLI daemon login：签发与 session 分离的 daemon Token；须已有工作区。
     */
    @Transactional
    public Map<String, Object> loginDaemon(String email, String password) {
        UserEntity user = requireValidUser(email, password);
        WorkspaceEntity workspace = findDefaultWorkspace(user.getId());
        if (workspace == null) {
            throw new IllegalArgumentException("请先在 Desktop 完成引导并创建工作区");
        }
        String daemonToken = issueToken(user.getId(), TokenTypes.DAEMON, "cli-daemon");
        Map<String, Object> payload = authPayload(user, workspace, null, daemonToken);
        payload.put("tokenType", TokenTypes.DAEMON);
        return payload;
    }

    public AuthPrincipal authenticateBearer(String rawToken, String expectedType) {
        if (!StringUtils.hasText(rawToken)) {
            return null;
        }
        String hash = TokenHasher.sha256Hex(rawToken.trim());
        UserTokenEntity token = userTokenMapper.selectOne(new LambdaQueryWrapper<UserTokenEntity>()
                .eq(UserTokenEntity::getTokenHash, hash)
                .eq(UserTokenEntity::getRevoked, 0)
                .last("LIMIT 1"));
        if (token == null) {
            return null;
        }
        if (expectedType != null && !expectedType.equals(token.getTokenType())) {
            return null;
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        UserEntity user = userMapper.selectById(token.getUserId());
        if (user == null) {
            return null;
        }
        WorkspaceEntity workspace = findDefaultWorkspace(user.getId());
        Long workspaceId = workspace == null ? null : workspace.getId();
        return new AuthPrincipal(user.getId(), user.getEmail(), token.getTokenType(), workspaceId);
    }

    public Map<String, Object> me(AuthPrincipal principal) {
        UserEntity user = userMapper.selectById(principal.userId());
        WorkspaceEntity workspace = findDefaultWorkspace(principal.userId());
        return authPayload(user, workspace, null, null);
    }

    /**
     * 引导页创建首个工作区（或额外工作区；MVP 每用户通常一个）。
     */
    @Transactional
    public Map<String, Object> createWorkspace(AuthPrincipal principal, String name, String slugInput,
                                               String issuePrefix) {
        if (principal == null || !TokenTypes.SESSION.equals(principal.tokenType())) {
            throw new IllegalArgumentException("需要会话登录");
        }
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("工作区名称不能为空");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() > 64) {
            throw new IllegalArgumentException("工作区名称过长");
        }

        String slug = StringUtils.hasText(slugInput) ? slugify(slugInput) : slugify(trimmedName);
        if (!StringUtils.hasText(slug)) {
            slug = "ws";
        }
        if (slugExists(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setName(trimmedName);
        workspace.setSlug(slug);
        workspace.setCreatedAt(LocalDateTime.now());
        workspace.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(workspace);

        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspace.getId());
        member.setUserId(principal.userId());
        member.setRole("owner");
        member.setCreatedAt(LocalDateTime.now());
        workspaceMemberMapper.insert(member);

        Map<String, Object> view = toWorkspaceView(workspace);
        if (StringUtils.hasText(issuePrefix)) {
            view.put("issuePrefix", issuePrefix.trim().toUpperCase(Locale.ROOT));
        } else {
            view.put("issuePrefix", deriveIssuePrefix(slug));
        }
        return view;
    }

    public void requireWorkspace(AuthPrincipal p) {
        if (p == null || p.workspaceId() == null) {
            throw new IllegalArgumentException("请先创建工作区");
        }
    }

    private UserEntity requireValidUser(String email, String password) {
        UserEntity user = findByEmail(normalizeEmail(email));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }
        return user;
    }

    private UserEntity findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email)
                .last("LIMIT 1"));
    }

    private WorkspaceEntity findDefaultWorkspace(Long userId) {
        WorkspaceMemberEntity member = workspaceMemberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                .eq(WorkspaceMemberEntity::getUserId, userId)
                .orderByAsc(WorkspaceMemberEntity::getId)
                .last("LIMIT 1"));
        if (member == null) {
            return null;
        }
        return workspaceMapper.selectById(member.getWorkspaceId());
    }

    private boolean slugExists(String slug) {
        Long cnt = workspaceMapper.selectCount(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getSlug, slug));
        return cnt != null && cnt > 0;
    }

    private String issueToken(Long userId, String type, String label) {
        String raw = TokenHasher.newRawToken();
        UserTokenEntity entity = new UserTokenEntity();
        entity.setUserId(userId);
        entity.setTokenType(type);
        entity.setTokenHash(TokenHasher.sha256Hex(raw));
        entity.setLabel(label);
        entity.setExpiresAt(LocalDateTime.now().plusDays(30));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setRevoked(0);
        userTokenMapper.insert(entity);
        return raw;
    }

    private Map<String, Object> authPayload(UserEntity user, WorkspaceEntity workspace,
                                            String sessionToken, String daemonToken) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("user", toUserView(user));
        map.put("workspace", workspace == null ? null : toWorkspaceView(workspace));
        map.put("needsOnboarding", workspace == null);
        if (sessionToken != null) {
            map.put("sessionToken", sessionToken);
        }
        if (daemonToken != null) {
            map.put("daemonToken", daemonToken);
        }
        return map;
    }

    private Map<String, Object> toUserView(UserEntity user) {
        return Map.of(
                "id", String.valueOf(user.getId()),
                "email", user.getEmail(),
                "displayName", user.getDisplayName()
        );
    }

    private Map<String, Object> toWorkspaceView(WorkspaceEntity workspace) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(workspace.getId()));
        m.put("name", workspace.getName());
        m.put("slug", workspace.getSlug());
        m.put("issuePrefix", deriveIssuePrefix(workspace.getSlug()));
        return m;
    }

    static String slugify(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9\\u4e00-\\u9fff-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        // URL 友好：中文转拼音成本高，MVP 用拼音替代为短 hash 片段
        if (s.matches(".*[\\u4e00-\\u9fff].*")) {
            String ascii = s.replaceAll("[\\u4e00-\\u9fff]+", "ws");
            ascii = ascii.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
            if (!StringUtils.hasText(ascii) || "ws".equals(ascii)) {
                return "ws-" + Integer.toHexString(raw.hashCode() & 0xffff);
            }
            return ascii;
        }
        return s;
    }

    static String deriveIssuePrefix(String slug) {
        if (!StringUtils.hasText(slug)) return "WS";
        String letters = slug.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);
        if (letters.length() >= 2) {
            return letters.substring(0, Math.min(4, letters.length()));
        }
        return "WS";
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String resolveDisplayName(String email, String displayName) {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
