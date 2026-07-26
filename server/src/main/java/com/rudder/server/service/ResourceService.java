package com.rudder.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rudder.server.auth.AuthPrincipal;
import com.rudder.server.domain.AgentEntity;
import com.rudder.server.domain.AgentSkillEntity;
import com.rudder.server.domain.InboxEntity;
import com.rudder.server.domain.ProjectEntity;
import com.rudder.server.domain.RuntimeEntity;
import com.rudder.server.domain.SkillEntity;
import com.rudder.server.mapper.AgentMapper;
import com.rudder.server.mapper.AgentSkillMapper;
import com.rudder.server.mapper.InboxMapper;
import com.rudder.server.mapper.ProjectMapper;
import com.rudder.server.mapper.RuntimeMapper;
import com.rudder.server.mapper.SkillMapper;
import com.rudder.server.ws.NettyWsHub;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Agent / Skill / Project / Runtime / Inbox 业务。 */
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final AgentMapper agentMapper;
    private final SkillMapper skillMapper;
    private final AgentSkillMapper agentSkillMapper;
    private final ProjectMapper projectMapper;
    private final RuntimeMapper runtimeMapper;
    private final InboxMapper inboxMapper;
    private final NettyWsHub wsHub;

    // -------- Agent --------
    public List<Map<String, Object>> listAgents(AuthPrincipal p) {
        return agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                        .eq(AgentEntity::getWorkspaceId, p.workspaceId())
                        .orderByDesc(AgentEntity::getId))
                .stream().map(this::agentView).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createAgent(AuthPrincipal p, Map<String, Object> body) {
        String provider = str(body.get("provider"));
        if (!WorkdirResolver.isAllowedProvider(provider)) {
            throw new IllegalArgumentException("不支持的 Provider，请选择 cursor / claude_code / codex / stub");
        }
        AgentEntity a = new AgentEntity();
        a.setWorkspaceId(p.workspaceId());
        a.setName(require(body.get("name"), "名称不能为空"));
        a.setAvatar(str(body.get("avatar")));
        a.setDescription(str(body.get("description")));
        a.setInstructions(str(body.get("instructions")));
        a.setProvider(provider.toLowerCase());
        // 须先有对应 Provider 的在线 Runtime（自动探测或手动添加）
        var runtimes = listRuntimes(p);
        Long bindRuntimeId = asLong(body.get("runtimeId"));
        boolean runtimeOnline = runtimes.stream().anyMatch(r -> {
            if (!provider.equalsIgnoreCase(String.valueOf(r.get("provider")))) return false;
            if (!"online".equals(String.valueOf(r.get("status")))) return false;
            if (bindRuntimeId == null) return true;
            return String.valueOf(bindRuntimeId).equals(String.valueOf(r.get("id")));
        });
        if (!runtimeOnline) {
            throw new IllegalArgumentException(
                    "运行时「" + provider + "」未添加或不在线。请先到「运行时」页确认本机已安装并在线");
        }
        if (bindRuntimeId == null) {
            bindRuntimeId = runtimes.stream()
                    .filter(r -> provider.equalsIgnoreCase(String.valueOf(r.get("provider")))
                            && "online".equals(String.valueOf(r.get("status"))))
                    .map(r -> asLong(r.get("id")))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        a.setRuntimeId(bindRuntimeId);
        a.setMaxConcurrency(body.get("maxConcurrency") == null ? 1 : ((Number) body.get("maxConcurrency")).intValue());
        a.setStatus("idle");
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        agentMapper.insert(a);
        bindSkills(a.getId(), body.get("skillIds"));
        return agentView(a);
    }

    @Transactional
    public Map<String, Object> updateAgent(AuthPrincipal p, Long id, Map<String, Object> body) {
        AgentEntity a = requireAgent(p, id);
        if (body.containsKey("name")) a.setName(require(body.get("name"), "名称不能为空"));
        if (body.containsKey("instructions")) a.setInstructions(str(body.get("instructions")));
        if (body.containsKey("description")) a.setDescription(str(body.get("description")));
        if (body.containsKey("provider")) {
            String provider = str(body.get("provider"));
            if (!WorkdirResolver.isAllowedProvider(provider)) {
                throw new IllegalArgumentException("不支持的 Provider");
            }
            a.setProvider(provider.toLowerCase());
        }
        if (body.containsKey("runtimeId")) a.setRuntimeId(asLong(body.get("runtimeId")));
        if (body.containsKey("skillIds")) {
            agentSkillMapper.delete(new LambdaQueryWrapper<AgentSkillEntity>().eq(AgentSkillEntity::getAgentId, id));
            bindSkills(id, body.get("skillIds"));
        }
        a.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(a);
        return agentView(a);
    }

    public void deleteAgent(AuthPrincipal p, Long id) {
        requireAgent(p, id);
        agentMapper.deleteById(id);
    }

    public AgentEntity requireAgent(AuthPrincipal p, Long id) {
        AgentEntity a = agentMapper.selectById(id);
        if (a == null || !Objects.equals(a.getWorkspaceId(), p.workspaceId())) {
            throw new IllegalArgumentException("Agent 不存在");
        }
        return a;
    }

    private void bindSkills(Long agentId, Object skillIds) {
        if (!(skillIds instanceof List<?> list)) return;
        for (Object o : list) {
            Long sid = asLong(o);
            if (sid == null) continue;
            AgentSkillEntity rel = new AgentSkillEntity();
            rel.setAgentId(agentId);
            rel.setSkillId(sid);
            rel.setCreatedAt(LocalDateTime.now());
            agentSkillMapper.insert(rel);
        }
    }

    private Map<String, Object> agentView(AgentEntity a) {
        List<Long> skillIds = agentSkillMapper.selectList(new LambdaQueryWrapper<AgentSkillEntity>()
                        .eq(AgentSkillEntity::getAgentId, a.getId()))
                .stream().map(AgentSkillEntity::getSkillId).collect(Collectors.toList());
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(a.getId()));
        m.put("name", a.getName());
        m.put("avatar", a.getAvatar());
        m.put("description", a.getDescription());
        m.put("instructions", a.getInstructions());
        m.put("provider", a.getProvider());
        m.put("runtimeId", a.getRuntimeId() == null ? null : String.valueOf(a.getRuntimeId()));
        m.put("maxConcurrency", a.getMaxConcurrency());
        m.put("status", a.getStatus());
        m.put("skillIds", skillIds.stream().map(String::valueOf).collect(Collectors.toList()));
        m.put("createdAt", a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        m.put("updatedAt", a.getUpdatedAt() == null ? null : a.getUpdatedAt().toString());
        return m;
    }

    // -------- Skill --------
    public List<Map<String, Object>> listSkills(AuthPrincipal p) {
        return skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                        .eq(SkillEntity::getWorkspaceId, p.workspaceId())
                        .orderByDesc(SkillEntity::getId))
                .stream().map(s -> Map.<String, Object>of(
                        "id", String.valueOf(s.getId()),
                        "name", s.getName(),
                        "content", s.getContent()
                )).collect(Collectors.toList());
    }

    public Map<String, Object> createSkill(AuthPrincipal p, Map<String, Object> body) {
        SkillEntity s = new SkillEntity();
        s.setWorkspaceId(p.workspaceId());
        s.setName(require(body.get("name"), "名称不能为空"));
        s.setContent(require(body.get("content"), "内容不能为空"));
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        skillMapper.insert(s);
        return Map.of("id", String.valueOf(s.getId()), "name", s.getName(), "content", s.getContent());
    }

    public List<SkillEntity> skillsOfAgent(Long agentId) {
        List<Long> ids = agentSkillMapper.selectList(new LambdaQueryWrapper<AgentSkillEntity>()
                        .eq(AgentSkillEntity::getAgentId, agentId))
                .stream().map(AgentSkillEntity::getSkillId).toList();
        if (ids.isEmpty()) return List.of();
        return skillMapper.selectBatchIds(ids);
    }

    // -------- Project --------
    public List<Map<String, Object>> listProjects(AuthPrincipal p) {
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                        .eq(ProjectEntity::getWorkspaceId, p.workspaceId())
                        .orderByDesc(ProjectEntity::getId))
                .stream().map(this::projectView).collect(Collectors.toList());
    }

    public Map<String, Object> createProject(AuthPrincipal p, Map<String, Object> body) {
        String localPath = str(body.get("localPath"));
        if (StringUtils.hasText(localPath)) {
            WorkdirResolver.validateLocalPath(localPath);
        }
        ProjectEntity pr = new ProjectEntity();
        pr.setWorkspaceId(p.workspaceId());
        pr.setName(require(body.get("name"), "名称不能为空"));
        pr.setLocalPath(StringUtils.hasText(localPath) ? localPath : null);
        pr.setCreatedAt(LocalDateTime.now());
        pr.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(pr);
        return projectView(pr);
    }

    public ProjectEntity requireProject(AuthPrincipal p, Long id) {
        if (id == null) return null;
        ProjectEntity pr = projectMapper.selectById(id);
        if (pr == null || !Objects.equals(pr.getWorkspaceId(), p.workspaceId())) {
            throw new IllegalArgumentException("项目不存在");
        }
        return pr;
    }

    private Map<String, Object> projectView(ProjectEntity pr) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(pr.getId()));
        m.put("name", pr.getName());
        m.put("localPath", pr.getLocalPath());
        return m;
    }

    // -------- Runtime --------
    /** 心跳超过该秒数视为离线（展示用）。 */
    private static final long RUNTIME_OFFLINE_AFTER_SECONDS = 45;

    public List<Map<String, Object>> listRuntimes(AuthPrincipal p) {
        return runtimeMapper.selectList(new LambdaQueryWrapper<RuntimeEntity>()
                        .eq(RuntimeEntity::getWorkspaceId, p.workspaceId())
                        .orderByDesc(RuntimeEntity::getLastHeartbeatAt))
                .stream().map(this::runtimeView).collect(Collectors.toList());
    }

    /**
     * Daemon 注册/刷新 Runtime：同一工作区 + Provider + Daemon 实例 只保留一条。
     * Desktop 与 CLI 使用不同 daemonId，可同机并存。
     */
    @Transactional
    public Map<String, Object> upsertRuntime(Long workspaceId, String daemonId, String provider,
                                             String hostName, String metaJson) {
        if (!WorkdirResolver.isAllowedProvider(provider)) {
            throw new IllegalArgumentException("不支持的 Provider: " + provider);
        }
        if (!StringUtils.hasText(daemonId)) {
            throw new IllegalArgumentException("daemonId 不能为空");
        }
        String prov = provider.toLowerCase();
        RuntimeEntity existing = runtimeMapper.selectOne(new LambdaQueryWrapper<RuntimeEntity>()
                .eq(RuntimeEntity::getWorkspaceId, workspaceId)
                .eq(RuntimeEntity::getProvider, prov)
                .eq(RuntimeEntity::getDaemonId, daemonId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            existing = new RuntimeEntity();
            existing.setWorkspaceId(workspaceId);
            existing.setDaemonId(daemonId);
            existing.setProvider(prov);
            existing.setHostName(hostName);
            existing.setStatus("online");
            existing.setLastHeartbeatAt(now);
            existing.setMetaJson(metaJson);
            existing.setCreatedAt(now);
            existing.setUpdatedAt(now);
            runtimeMapper.insert(existing);
        } else {
            existing.setHostName(hostName);
            existing.setStatus("online");
            existing.setLastHeartbeatAt(now);
            existing.setMetaJson(metaJson);
            existing.setUpdatedAt(now);
            runtimeMapper.updateById(existing);
        }
        wsHub.publish(workspaceId, Map.of("type", "runtime.updated", "runtime", runtimeView(existing)));
        return runtimeView(existing);
    }

    @Transactional
    public void deleteRuntime(AuthPrincipal p, Long id) {
        RuntimeEntity r = runtimeMapper.selectById(id);
        if (r == null || !Objects.equals(r.getWorkspaceId(), p.workspaceId())) {
            throw new IllegalArgumentException("运行时不存在");
        }
        runtimeMapper.deleteById(id);
        wsHub.publish(p.workspaceId(), Map.of("type", "runtime.deleted", "id", String.valueOf(id)));
    }

    /**
     * 会话侧添加运行时：写入当前登录用户工作区。
     * daemonId 建议传 Desktop profile 的稳定实例 ID，便于随后由 Desktop Daemon 接管心跳。
     */
    @Transactional
    public Map<String, Object> addRuntimeForSession(AuthPrincipal p, String provider, String hostName,
                                                    String daemonId) {
        if (!WorkdirResolver.isAllowedProvider(provider)) {
            throw new IllegalArgumentException("不支持的 Provider: " + provider);
        }
        String host = StringUtils.hasText(hostName) ? hostName : "";
        String id = StringUtils.hasText(daemonId) ? daemonId : "session-add";
        String meta = "{\"profile\":\"desktop\",\"source\":\"session-add\"}";
        return upsertRuntime(p.workspaceId(), id, provider.toLowerCase(), host, meta);
    }

    /**
     * 按 Provider 删除；若指定 daemonId 则只删该实例，否则删当前工作区该 Provider 全部行。
     */
    @Transactional
    public void deleteRuntimeByProvider(AuthPrincipal p, String provider, String daemonId) {
        if (!StringUtils.hasText(provider)) {
            throw new IllegalArgumentException("provider 不能为空");
        }
        LambdaQueryWrapper<RuntimeEntity> q = new LambdaQueryWrapper<RuntimeEntity>()
                .eq(RuntimeEntity::getWorkspaceId, p.workspaceId())
                .eq(RuntimeEntity::getProvider, provider.toLowerCase());
        if (StringUtils.hasText(daemonId)) {
            q.eq(RuntimeEntity::getDaemonId, daemonId);
        }
        List<RuntimeEntity> list = runtimeMapper.selectList(q);
        for (RuntimeEntity r : list) {
            runtimeMapper.deleteById(r.getId());
            wsHub.publish(p.workspaceId(), Map.of("type", "runtime.deleted", "id", String.valueOf(r.getId())));
        }
    }

    public void heartbeat(Long runtimeId) {
        RuntimeEntity r = runtimeMapper.selectById(runtimeId);
        if (r == null) return;
        r.setLastHeartbeatAt(LocalDateTime.now());
        r.setStatus("online");
        r.setUpdatedAt(LocalDateTime.now());
        runtimeMapper.updateById(r);
    }

    private Map<String, Object> runtimeView(RuntimeEntity r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(r.getId()));
        m.put("daemonId", r.getDaemonId());
        m.put("provider", r.getProvider());
        m.put("hostName", r.getHostName());
        String status = r.getStatus();
        if (r.getLastHeartbeatAt() != null
                && r.getLastHeartbeatAt().isBefore(LocalDateTime.now().minusSeconds(RUNTIME_OFFLINE_AFTER_SECONDS))) {
            status = "offline";
        }
        m.put("status", status);
        m.put("lastHeartbeatAt", r.getLastHeartbeatAt() == null ? null : r.getLastHeartbeatAt().toString());
        // 从 meta 提取 profile，便于 UI 区分 Desktop/CLI
        String profile = "";
        if (StringUtils.hasText(r.getMetaJson()) && r.getMetaJson().contains("profile")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(r.getMetaJson(), Map.class);
                Object pv = meta.get("profile");
                if (pv != null) profile = String.valueOf(pv);
            } catch (Exception ignored) {
                /* ignore */
            }
        }
        m.put("profile", profile);
        return m;
    }

    // -------- Inbox --------
    public void notifyUser(Long workspaceId, Long userId, String title, String body, String refType, Long refId) {
        InboxEntity inbox = new InboxEntity();
        inbox.setWorkspaceId(workspaceId);
        inbox.setUserId(userId);
        inbox.setTitle(title);
        inbox.setBody(body == null ? "" : body);
        inbox.setRefType(refType);
        inbox.setRefId(refId);
        inbox.setReadFlag(0);
        inbox.setCreatedAt(LocalDateTime.now());
        inboxMapper.insert(inbox);
        wsHub.publish(workspaceId, Map.of("type", "inbox.created", "title", title));
    }

    public List<Map<String, Object>> listInbox(AuthPrincipal p) {
        return inboxMapper.selectList(new LambdaQueryWrapper<InboxEntity>()
                        .eq(InboxEntity::getUserId, p.userId())
                        .orderByDesc(InboxEntity::getId)
                        .last("LIMIT 100"))
                .stream().map(i -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", String.valueOf(i.getId()));
                    m.put("title", i.getTitle());
                    m.put("body", i.getBody());
                    m.put("read", i.getReadFlag() != null && i.getReadFlag() == 1);
                    m.put("createdAt", i.getCreatedAt().toString());
                    return m;
                }).collect(Collectors.toList());
    }

    public long unreadCount(AuthPrincipal p) {
        return inboxMapper.selectCount(new LambdaQueryWrapper<InboxEntity>()
                .eq(InboxEntity::getUserId, p.userId())
                .eq(InboxEntity::getReadFlag, 0));
    }

    public void markRead(AuthPrincipal p, Long id) {
        InboxEntity i = inboxMapper.selectById(id);
        if (i == null || !Objects.equals(i.getUserId(), p.userId())) {
            throw new IllegalArgumentException("通知不存在");
        }
        i.setReadFlag(1);
        inboxMapper.updateById(i);
    }

    private static String require(Object v, String msg) {
        String s = str(v);
        if (!StringUtils.hasText(s)) throw new IllegalArgumentException(msg);
        return s;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Long asLong(Object v) {
        if (v == null || "".equals(v)) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }
}
