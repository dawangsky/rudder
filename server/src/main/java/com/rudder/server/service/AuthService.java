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
 * 认证与默认工作区：注册、登录、双 Token 签发与校验。
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

        WorkspaceEntity workspace = createDefaultWorkspace(user);
        String sessionToken = issueToken(user.getId(), TokenTypes.SESSION, "desktop-session");

        return authPayload(user, workspace, sessionToken, null);
    }

    @Transactional
    public Map<String, Object> loginSession(String email, String password) {
        UserEntity user = requireValidUser(email, password);
        WorkspaceEntity workspace = requireDefaultWorkspace(user.getId());
        String sessionToken = issueToken(user.getId(), TokenTypes.SESSION, "desktop-session");
        return authPayload(user, workspace, sessionToken, null);
    }

    /**
     * CLI daemon login：签发与 session 分离的 daemon Token。
     */
    @Transactional
    public Map<String, Object> loginDaemon(String email, String password) {
        UserEntity user = requireValidUser(email, password);
        WorkspaceEntity workspace = requireDefaultWorkspace(user.getId());
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
        WorkspaceEntity workspace = requireDefaultWorkspace(user.getId());
        return new AuthPrincipal(user.getId(), user.getEmail(), token.getTokenType(), workspace.getId());
    }

    public Map<String, Object> me(AuthPrincipal principal) {
        UserEntity user = userMapper.selectById(principal.userId());
        WorkspaceEntity workspace = requireDefaultWorkspace(principal.userId());
        return Map.of(
                "user", toUserView(user),
                "workspace", toWorkspaceView(workspace)
        );
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

    private WorkspaceEntity createDefaultWorkspace(UserEntity user) {
        String base = user.getEmail().substring(0, user.getEmail().indexOf('@'))
                .replaceAll("[^a-zA-Z0-9-]", "-")
                .toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(base)) {
            base = "ws";
        }
        String slug = base + "-" + UUID.randomUUID().toString().substring(0, 8);

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setName(user.getDisplayName() + " 的工作区");
        workspace.setSlug(slug);
        workspace.setCreatedAt(LocalDateTime.now());
        workspace.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(workspace);

        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspace.getId());
        member.setUserId(user.getId());
        member.setRole("owner");
        member.setCreatedAt(LocalDateTime.now());
        workspaceMemberMapper.insert(member);
        return workspace;
    }

    private WorkspaceEntity requireDefaultWorkspace(Long userId) {
        WorkspaceMemberEntity member = workspaceMemberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                .eq(WorkspaceMemberEntity::getUserId, userId)
                .orderByAsc(WorkspaceMemberEntity::getId)
                .last("LIMIT 1"));
        if (member == null) {
            throw new IllegalStateException("用户未关联工作区");
        }
        WorkspaceEntity workspace = workspaceMapper.selectById(member.getWorkspaceId());
        if (workspace == null) {
            throw new IllegalStateException("工作区不存在");
        }
        return workspace;
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
        map.put("workspace", toWorkspaceView(workspace));
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
        return Map.of(
                "id", String.valueOf(workspace.getId()),
                "name", workspace.getName(),
                "slug", workspace.getSlug()
        );
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
