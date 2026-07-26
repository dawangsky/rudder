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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 认证与工作区：注册不自动建工作区；引导信息落库；一账号可加入多个工作区。
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

        String sessionToken = issueToken(user.getId(), TokenTypes.SESSION, "desktop-session");
        return authPayload(user, null, sessionToken, null);
    }

    @Transactional
    public Map<String, Object> loginSession(String email, String password) {
        UserEntity user = requireValidUser(email, password);
        WorkspaceEntity workspace = resolveActiveWorkspace(user);
        String sessionToken = issueToken(user.getId(), TokenTypes.SESSION, "desktop-session");
        return authPayload(user, workspace, sessionToken, null);
    }

    @Transactional
    public Map<String, Object> loginDaemon(String email, String password) {
        UserEntity user = requireValidUser(email, password);
        WorkspaceEntity workspace = resolveActiveWorkspace(user);
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
        WorkspaceEntity workspace = resolveActiveWorkspace(user);
        Long workspaceId = workspace == null ? null : workspace.getId();
        return new AuthPrincipal(user.getId(), user.getEmail(), token.getTokenType(), workspaceId);
    }

    public Map<String, Object> me(AuthPrincipal principal) {
        UserEntity user = userMapper.selectById(principal.userId());
        WorkspaceEntity workspace = resolveActiveWorkspace(user);
        Map<String, Object> payload = authPayload(user, workspace, null, null);
        payload.put("workspaces", listWorkspaceViews(principal.userId()));
        return payload;
    }

    /** 引导第 1 步：保存角色与使用目的到用户表。 */
    @Transactional
    public Map<String, Object> saveOnboardingProfile(AuthPrincipal principal, String role, String intent) {
        requireSession(principal);
        UserEntity user = userMapper.selectById(principal.userId());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (StringUtils.hasText(role)) {
            user.setOnboardRole(role.trim());
        }
        if (StringUtils.hasText(intent)) {
            user.setOnboardIntent(intent.trim());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toUserView(user);
    }

    /**
     * 创建工作区并加入为 owner；可同时写入引导角色/用途与 issue 前缀。
     * 一账号可多次调用创建多个工作区。
     */
    @Transactional
    public Map<String, Object> createWorkspace(AuthPrincipal principal, String name, String slugInput,
                                               String issuePrefix, String role, String intent) {
        requireSession(principal);
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("工作区名称不能为空");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() > 64) {
            throw new IllegalArgumentException("工作区名称过长");
        }

        UserEntity user = userMapper.selectById(principal.userId());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (StringUtils.hasText(role)) {
            user.setOnboardRole(role.trim());
        }
        if (StringUtils.hasText(intent)) {
            user.setOnboardIntent(intent.trim());
        }

        String slug = StringUtils.hasText(slugInput) ? slugify(slugInput) : slugify(trimmedName);
        if (!StringUtils.hasText(slug)) {
            slug = "ws";
        }
        if (slugExists(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        String prefix = StringUtils.hasText(issuePrefix)
                ? issuePrefix.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "")
                : deriveIssuePrefix(slug);
        if (!StringUtils.hasText(prefix)) {
            prefix = "WS";
        }
        if (prefix.length() > 16) {
            prefix = prefix.substring(0, 16);
        }

        LocalDateTime now = LocalDateTime.now();
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setName(trimmedName);
        workspace.setSlug(slug);
        workspace.setIssuePrefix(prefix);
        workspace.setCreatedBy(principal.userId());
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        workspaceMapper.insert(workspace);

        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspace.getId());
        member.setUserId(principal.userId());
        member.setRole("owner");
        member.setLastAccessedAt(now);
        member.setCreatedAt(now);
        workspaceMemberMapper.insert(member);

        user.setActiveWorkspaceId(workspace.getId());
        user.setUpdatedAt(now);
        userMapper.updateById(user);

        return toWorkspaceView(workspace, "owner");
    }

    /** 列出当前用户加入的全部工作区。 */
    public List<Map<String, Object>> listWorkspaces(AuthPrincipal principal) {
        requireSession(principal);
        return listWorkspaceViews(principal.userId());
    }

    /** 当前工作区成员（负责人选择等）。 */
    public List<Map<String, Object>> listWorkspaceMembers(AuthPrincipal principal) {
        requireSession(principal);
        if (principal.workspaceId() == null) {
            throw new IllegalArgumentException("尚未加入工作区");
        }
        List<WorkspaceMemberEntity> members = workspaceMemberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getWorkspaceId, principal.workspaceId())
                        .orderByAsc(WorkspaceMemberEntity::getId));
        if (members.isEmpty()) return List.of();
        List<Long> userIds = members.stream().map(WorkspaceMemberEntity::getUserId).toList();
        Map<Long, UserEntity> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u, (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (WorkspaceMemberEntity m : members) {
            UserEntity u = users.get(m.getUserId());
            if (u == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(u.getId()));
            row.put("email", u.getEmail());
            row.put("displayName", StringUtils.hasText(u.getDisplayName()) ? u.getDisplayName() : u.getEmail());
            row.put("role", m.getRole());
            out.add(row);
        }
        return out;
    }

    /** 切换当前工作区（须为成员）。 */
    @Transactional
    public Map<String, Object> switchWorkspace(AuthPrincipal principal, Long workspaceId) {
        requireSession(principal);
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId 不能为空");
        }
        WorkspaceMemberEntity member = workspaceMemberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                .eq(WorkspaceMemberEntity::getUserId, principal.userId())
                .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                .last("LIMIT 1"));
        if (member == null) {
            throw new IllegalArgumentException("无权访问该工作区");
        }
        WorkspaceEntity workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new IllegalArgumentException("工作区不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        member.setLastAccessedAt(now);
        workspaceMemberMapper.updateById(member);

        UserEntity user = userMapper.selectById(principal.userId());
        user.setActiveWorkspaceId(workspaceId);
        user.setUpdatedAt(now);
        userMapper.updateById(user);

        return toWorkspaceView(workspace, member.getRole());
    }

    public void requireWorkspace(AuthPrincipal p) {
        if (p == null || p.workspaceId() == null) {
            throw new IllegalArgumentException("请先创建工作区");
        }
    }

    private void requireSession(AuthPrincipal principal) {
        if (principal == null || !TokenTypes.SESSION.equals(principal.tokenType())) {
            throw new IllegalArgumentException("需要会话登录");
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

    /**
     * 解析当前工作区：优先 active_workspace_id（且仍为成员），否则最近访问，再否则最早加入。
     */
    private WorkspaceEntity resolveActiveWorkspace(UserEntity user) {
        if (user == null) return null;
        if (user.getActiveWorkspaceId() != null) {
            WorkspaceMemberEntity m = workspaceMemberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                    .eq(WorkspaceMemberEntity::getUserId, user.getId())
                    .eq(WorkspaceMemberEntity::getWorkspaceId, user.getActiveWorkspaceId())
                    .last("LIMIT 1"));
            if (m != null) {
                WorkspaceEntity ws = workspaceMapper.selectById(user.getActiveWorkspaceId());
                if (ws != null) return ws;
            }
        }
        List<WorkspaceMemberEntity> members = workspaceMemberMapper.selectList(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                .eq(WorkspaceMemberEntity::getUserId, user.getId())
                .orderByDesc(WorkspaceMemberEntity::getLastAccessedAt)
                .orderByAsc(WorkspaceMemberEntity::getId)
                .last("LIMIT 1"));
        if (members.isEmpty()) {
            return null;
        }
        return workspaceMapper.selectById(members.get(0).getWorkspaceId());
    }

    private List<Map<String, Object>> listWorkspaceViews(Long userId) {
        List<WorkspaceMemberEntity> members = workspaceMemberMapper.selectList(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                .eq(WorkspaceMemberEntity::getUserId, userId)
                .orderByDesc(WorkspaceMemberEntity::getLastAccessedAt)
                .orderByAsc(WorkspaceMemberEntity::getId));
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> ids = members.stream().map(WorkspaceMemberEntity::getWorkspaceId).toList();
        Map<Long, WorkspaceEntity> byId = workspaceMapper.selectList(new LambdaQueryWrapper<WorkspaceEntity>()
                        .in(WorkspaceEntity::getId, ids))
                .stream()
                .collect(Collectors.toMap(WorkspaceEntity::getId, w -> w, (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (WorkspaceMemberEntity m : members) {
            WorkspaceEntity w = byId.get(m.getWorkspaceId());
            if (w != null) {
                out.add(toWorkspaceView(w, m.getRole()));
            }
        }
        return out;
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
        map.put("workspace", workspace == null ? null : toWorkspaceView(workspace, null));
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
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(user.getId()));
        m.put("email", user.getEmail());
        m.put("displayName", user.getDisplayName());
        m.put("onboardRole", user.getOnboardRole());
        m.put("onboardIntent", user.getOnboardIntent());
        m.put("activeWorkspaceId",
                user.getActiveWorkspaceId() == null ? null : String.valueOf(user.getActiveWorkspaceId()));
        return m;
    }

    private Map<String, Object> toWorkspaceView(WorkspaceEntity workspace, String memberRole) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(workspace.getId()));
        m.put("name", workspace.getName());
        m.put("slug", workspace.getSlug());
        String prefix = StringUtils.hasText(workspace.getIssuePrefix())
                ? workspace.getIssuePrefix()
                : deriveIssuePrefix(workspace.getSlug());
        m.put("issuePrefix", prefix);
        if (workspace.getCreatedBy() != null) {
            m.put("createdBy", String.valueOf(workspace.getCreatedBy()));
        }
        if (memberRole != null) {
            m.put("role", memberRole);
        }
        return m;
    }

    static String slugify(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9\\u4e00-\\u9fff-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
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
