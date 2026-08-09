package com.rudder.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rudder.server.auth.AuthPrincipal;
import com.rudder.server.domain.AgentEntity;
import com.rudder.server.domain.AgentSkillEntity;
import com.rudder.server.domain.ChatMessageEntity;
import com.rudder.server.domain.ChatSessionEntity;
import com.rudder.server.domain.InboxEntity;
import com.rudder.server.domain.IssueEntity;
import com.rudder.server.domain.ProjectEntity;
import com.rudder.server.domain.RuntimeEntity;
import com.rudder.server.domain.RuntimeSkillEntity;
import com.rudder.server.domain.SkillEntity;
import com.rudder.server.domain.TaskEntity;
import com.rudder.server.domain.UserEntity;
import com.rudder.server.mapper.AgentMapper;
import com.rudder.server.mapper.AgentSkillMapper;
import com.rudder.server.mapper.ChatMessageMapper;
import com.rudder.server.mapper.ChatSessionMapper;
import com.rudder.server.mapper.InboxMapper;
import com.rudder.server.mapper.IssueMapper;
import com.rudder.server.mapper.ProjectMapper;
import com.rudder.server.mapper.RuntimeMapper;
import com.rudder.server.mapper.RuntimeSkillMapper;
import com.rudder.server.mapper.SkillMapper;
import com.rudder.server.mapper.TaskMapper;
import com.rudder.server.mapper.UserMapper;
import com.rudder.server.ws.NettyWsHub;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Agent / Skill / Project / Runtime / Inbox 业务。 */
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final AgentMapper agentMapper;
    private final SkillMapper skillMapper;
    private final AgentSkillMapper agentSkillMapper;
    private final RuntimeSkillMapper runtimeSkillMapper;
    private final ProjectMapper projectMapper;
    private final RuntimeMapper runtimeMapper;
    private final ProtocolService protocolService;
    private final TaskMapper taskMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final IssueMapper issueMapper;
    private final InboxMapper inboxMapper;
    private final UserMapper userMapper;
    private final NettyWsHub wsHub;
    private final SkillImportService skillImportService;

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
        if (!protocolService.isAllowedProvider(p.workspaceId(), provider)) {
            throw new IllegalArgumentException("不支持的 Provider（含自定义 custom_*）");
        }
        AgentEntity a = new AgentEntity();
        a.setWorkspaceId(p.workspaceId());
        a.setName(require(body.get("name"), "名称不能为空"));
        String avatar = str(body.get("avatar"));
        if (avatar.length() > 512) {
            throw new IllegalArgumentException("头像地址过长");
        }
        a.setAvatar(avatar);
        a.setDescription(str(body.get("description")));
        a.setInstructions(str(body.get("instructions")));
        a.setProvider(provider.toLowerCase());
        // 须先有对应 Provider 的在线 Runtime（内置探测或自定义命令）
        var runtimes = listRuntimes(p);
        final Long requestedRuntimeId = asLong(body.get("runtimeId"));
        boolean runtimeOnline = runtimes.stream().anyMatch(r -> {
            if (!"online".equals(String.valueOf(r.get("status")))) return false;
            if (requestedRuntimeId != null) {
                return String.valueOf(requestedRuntimeId).equals(String.valueOf(r.get("id")));
            }
            return provider.equalsIgnoreCase(String.valueOf(r.get("provider")));
        });
        if (!runtimeOnline) {
            throw new IllegalArgumentException(
                    "运行时「" + provider + "」未添加或不在线。请先到「运行时」页确认本机已安装并在线");
        }
        Long bindRuntimeId = requestedRuntimeId;
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
        a.setModel(normalizeModel(body.get("model")));
        a.setThinkingMode(normalizeThinkingMode(body.get("thinkingMode")));
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
        if (body.containsKey("avatar")) {
            String avatar = str(body.get("avatar"));
            if (avatar.length() > 512) {
                throw new IllegalArgumentException("头像地址过长");
            }
            a.setAvatar(avatar);
        }
        if (body.containsKey("instructions")) a.setInstructions(str(body.get("instructions")));
        if (body.containsKey("description")) a.setDescription(str(body.get("description")));
        if (body.containsKey("provider")) {
            String provider = str(body.get("provider"));
            if (!protocolService.isAllowedProvider(p.workspaceId(), provider)) {
                throw new IllegalArgumentException("不支持的 Provider");
            }
            a.setProvider(provider.toLowerCase());
        }
        if (body.containsKey("runtimeId")) a.setRuntimeId(asLong(body.get("runtimeId")));
        if (body.containsKey("model")) a.setModel(normalizeModel(body.get("model")));
        if (body.containsKey("thinkingMode")) a.setThinkingMode(normalizeThinkingMode(body.get("thinkingMode")));
        if (body.containsKey("maxConcurrency")) {
            int n = body.get("maxConcurrency") == null ? 1 : ((Number) body.get("maxConcurrency")).intValue();
            if (n < 1 || n > 50) {
                throw new IllegalArgumentException("并发须在 1–50 之间");
            }
            a.setMaxConcurrency(n);
        }
        if (body.containsKey("status")) {
            String status = str(body.get("status")).toLowerCase();
            if (!StringUtils.hasText(status)) {
                throw new IllegalArgumentException("状态不能为空");
            }
            if (!"idle".equals(status) && !"archived".equals(status)
                    && !"busy".equals(status) && !"online".equals(status)) {
                throw new IllegalArgumentException("不支持的状态: " + status);
            }
            a.setStatus(status);
        }
        if (body.containsKey("skillIds")) {
            agentSkillMapper.delete(new LambdaQueryWrapper<AgentSkillEntity>().eq(AgentSkillEntity::getAgentId, id));
            bindSkills(id, body.get("skillIds"));
        }
        a.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(a);
        Map<String, Object> view = agentView(a);
        wsHub.publish(p.workspaceId(), Map.of("type", "agent.updated", "agent", view));
        return view;
    }

    /** 归档：保留全部 task，恢复后可继续；不可再领取新任务。 */
    @Transactional
    public Map<String, Object> archiveAgent(AuthPrincipal p, Long id) {
        AgentEntity a = requireAgent(p, id);
        if ("archived".equalsIgnoreCase(a.getStatus())) {
            return agentView(a);
        }
        a.setStatus("archived");
        a.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(a);
        Map<String, Object> view = agentView(a);
        wsHub.publish(p.workspaceId(), Map.of("type", "agent.updated", "agent", view));
        return view;
    }

    /** 从归档恢复为 idle，历史 task 原样保留。 */
    @Transactional
    public Map<String, Object> restoreAgent(AuthPrincipal p, Long id) {
        AgentEntity a = requireAgent(p, id);
        if (!"archived".equalsIgnoreCase(a.getStatus())) {
            throw new IllegalArgumentException("仅已归档智能体可恢复");
        }
        a.setStatus("idle");
        a.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(a);
        Map<String, Object> view = agentView(a);
        wsHub.publish(p.workspaceId(), Map.of("type", "agent.updated", "agent", view));
        return view;
    }

    /** 已归档智能体不可再入队 / 开聊。 */
    public void assertAgentActive(AgentEntity a) {
        if (a != null && "archived".equalsIgnoreCase(a.getStatus())) {
            throw new IllegalArgumentException("智能体已归档，无法使用");
        }
    }

    /**
     * 永久删除智能体并抹去相关记录：skills 绑定、task、chat 会话与消息；
     * Issue 指派到该 Agent 的取消指派。Agent 行物理删除。
     */
    @Transactional
    public void deleteAgent(AuthPrincipal p, Long id) {
        AgentEntity a = requireAgent(p, id);
        // skills
        agentSkillMapper.delete(new LambdaQueryWrapper<AgentSkillEntity>().eq(AgentSkillEntity::getAgentId, id));
        // tasks
        taskMapper.delete(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getWorkspaceId, p.workspaceId())
                .eq(TaskEntity::getAgentId, id));
        // chats
        List<ChatSessionEntity> sessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getWorkspaceId, p.workspaceId())
                .eq(ChatSessionEntity::getAgentId, id));
        for (ChatSessionEntity s : sessions) {
            chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                    .eq(ChatMessageEntity::getSessionId, s.getId()));
            chatSessionMapper.deleteById(s.getId());
        }
        // issues assigned to this agent
        List<IssueEntity> issues = issueMapper.selectList(new LambdaQueryWrapper<IssueEntity>()
                .eq(IssueEntity::getWorkspaceId, p.workspaceId())
                .eq(IssueEntity::getAssigneeType, "agent")
                .eq(IssueEntity::getAssigneeId, id));
        LocalDateTime now = LocalDateTime.now();
        for (IssueEntity issue : issues) {
            issue.setAssigneeType(null);
            issue.setAssigneeId(null);
            issue.setUpdatedAt(now);
            issueMapper.updateById(issue);
        }
        // agent 物理删除（绕过 @TableLogic）
        agentMapper.purgeById(id);
        wsHub.publish(p.workspaceId(), Map.of("type", "agent.deleted", "id", String.valueOf(id), "name", a.getName()));
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
        m.put("model", StringUtils.hasText(a.getModel()) ? a.getModel() : "default");
        m.put("thinkingMode", StringUtils.hasText(a.getThinkingMode()) ? a.getThinkingMode() : "cli");
        m.put("maxConcurrency", a.getMaxConcurrency());
        m.put("status", a.getStatus());
        m.put("skillIds", skillIds.stream().map(String::valueOf).collect(Collectors.toList()));
        m.put("createdAt", a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        m.put("updatedAt", a.getUpdatedAt() == null ? null : a.getUpdatedAt().toString());
        return m;
    }

    // -------- Skill --------
    public List<Map<String, Object>> listSkills(AuthPrincipal p) {
        List<SkillEntity> skills = skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getWorkspaceId, p.workspaceId())
                .orderByDesc(SkillEntity::getUpdatedAt)
                .orderByDesc(SkillEntity::getId));
        return enrichSkillViews(p, skills, false);
    }

    public Map<String, Object> getSkill(AuthPrincipal p, Long id) {
        SkillEntity s = requireSkill(p, id);
        List<Map<String, Object>> views = enrichSkillViews(p, List.of(s), true);
        return views.isEmpty() ? skillView(s) : views.get(0);
    }

    /**
     * 创建或同名更新。返回字段含 action=created|updated。
     */
    @Transactional
    public Map<String, Object> createSkill(AuthPrincipal p, Map<String, Object> body) {
        String content = require(body.get("content"), "内容不能为空");
        SkillMarkdown.Parsed parsed = SkillMarkdown.parse(content);
        String name = str(body.get("name"));
        if (!StringUtils.hasText(name)) name = parsed.name();
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("名称不能为空");
        }
        String description = str(body.get("description"));
        if (!StringUtils.hasText(description)) description = parsed.description();
        String sourceType = str(body.get("sourceType"));
        if (!StringUtils.hasText(sourceType)) sourceType = "manual";
        String sourceRef = StringUtils.hasText(str(body.get("sourceRef"))) ? str(body.get("sourceRef")) : null;

        SkillEntity existing = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getWorkspaceId, p.workspaceId())
                .eq(SkillEntity::getName, name)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setDescription(StringUtils.hasText(description) ? description : null);
            existing.setContent(content);
            existing.setSourceType(sourceType);
            existing.setSourceRef(sourceRef);
            existing.setUpdatedAt(now);
            skillMapper.updateById(existing);
            Map<String, Object> view = getSkill(p, existing.getId());
            view.put("action", "updated");
            return view;
        }

        SkillEntity s = new SkillEntity();
        s.setWorkspaceId(p.workspaceId());
        s.setName(name);
        s.setDescription(StringUtils.hasText(description) ? description : null);
        s.setContent(content);
        s.setSourceType(sourceType);
        s.setSourceRef(sourceRef);
        s.setCreatedByUserId(p.userId());
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        skillMapper.insert(s);
        Map<String, Object> view = getSkill(p, s.getId());
        view.put("action", "created");
        return view;
    }

    @Transactional
    public Map<String, Object> updateSkill(AuthPrincipal p, Long id, Map<String, Object> body) {
        SkillEntity s = requireSkill(p, id);
        boolean touched = false;
        if (body.containsKey("name")) {
            String name = str(body.get("name")).trim();
            if (!StringUtils.hasText(name)) throw new IllegalArgumentException("名称不能为空");
            SkillEntity clash = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                    .eq(SkillEntity::getWorkspaceId, p.workspaceId())
                    .eq(SkillEntity::getName, name)
                    .ne(SkillEntity::getId, id)
                    .last("LIMIT 1"));
            if (clash != null) throw new IllegalArgumentException("同名 skill 已存在");
            s.setName(name);
            touched = true;
        }
        if (body.containsKey("description")) {
            String description = str(body.get("description"));
            s.setDescription(StringUtils.hasText(description) ? description : null);
            touched = true;
        }
        if (body.containsKey("content")) {
            String content = require(body.get("content"), "内容不能为空");
            s.setContent(content);
            SkillMarkdown.Parsed parsed = SkillMarkdown.parse(content);
            if (!body.containsKey("name") && StringUtils.hasText(parsed.name())) {
                s.setName(parsed.name());
            }
            if (!body.containsKey("description") && StringUtils.hasText(parsed.description())) {
                s.setDescription(parsed.description());
            }
            touched = true;
        } else if (touched && !body.containsKey("files")) {
            s.setContent(SkillMarkdown.syncFrontmatter(s.getContent(), s.getName(),
                    s.getDescription() == null ? "" : s.getDescription()));
        }
        if (body.containsKey("files")) {
            s.setFilesJson(serializeSkillFiles(normalizeSkillFiles(body.get("files"))));
            touched = true;
        }
        if (!touched) throw new IllegalArgumentException("没有可更新的字段");
        s.setUpdatedAt(LocalDateTime.now());
        skillMapper.updateById(s);
        return getSkill(p, s.getId());
    }

    public Map<String, Object> importSkillFromUrl(AuthPrincipal p, Map<String, Object> body) {
        SkillImportService.Imported imported = skillImportService.fetch(require(body.get("url"), "URL 不能为空"));
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("name", imported.name());
        createBody.put("description", imported.description());
        createBody.put("content", imported.content());
        createBody.put("sourceType", "url");
        createBody.put("sourceRef", imported.sourceUrl());
        return createSkill(p, createBody);
    }

    /** 预览 URL 内容，不入库。 */
    public Map<String, Object> previewSkillUrl(AuthPrincipal p, Map<String, Object> body) {
        SkillImportService.Imported imported = skillImportService.fetch(require(body.get("url"), "URL 不能为空"));
        Map<String, Object> m = new HashMap<>();
        m.put("name", imported.name());
        m.put("description", imported.description() == null ? "" : imported.description());
        m.put("content", imported.content());
        m.put("sourceUrl", imported.sourceUrl());
        return m;
    }

    @Transactional
    public void deleteSkill(AuthPrincipal p, Long id) {
        SkillEntity s = requireSkill(p, id);
        agentSkillMapper.delete(new LambdaQueryWrapper<AgentSkillEntity>().eq(AgentSkillEntity::getSkillId, id));
        skillMapper.deleteById(s.getId());
    }

    @Transactional
    public Map<String, Object> deleteSkills(AuthPrincipal p, Map<String, Object> body) {
        List<Long> ids = asLongList(body.get("skillIds"));
        int n = 0;
        for (Long id : ids) {
            try {
                deleteSkill(p, id);
                n++;
            } catch (IllegalArgumentException ignored) {
                // skip missing
            }
        }
        return Map.of("ok", true, "deleted", n);
    }

    /** 将多个 skill 挂到多个 agent（已存在绑定则跳过）。 */
    @Transactional
    public Map<String, Object> bindSkillsToAgents(AuthPrincipal p, Map<String, Object> body) {
        List<Long> skillIds = asLongList(body.get("skillIds"));
        List<Long> agentIds = asLongList(body.get("agentIds"));
        if (skillIds.isEmpty() || agentIds.isEmpty()) {
            throw new IllegalArgumentException("skillIds 与 agentIds 不能为空");
        }
        Set<Long> validSkillIds = new HashSet<>();
        for (Long sid : skillIds) {
            SkillEntity s = skillMapper.selectById(sid);
            if (s != null && Objects.equals(s.getWorkspaceId(), p.workspaceId())) {
                validSkillIds.add(sid);
            }
        }
        Set<Long> validAgentIds = new HashSet<>();
        for (Long aid : agentIds) {
            AgentEntity a = agentMapper.selectById(aid);
            if (a != null && Objects.equals(a.getWorkspaceId(), p.workspaceId())) {
                validAgentIds.add(aid);
            }
        }
        if (validSkillIds.isEmpty() || validAgentIds.isEmpty()) {
            throw new IllegalArgumentException("无效的 skill 或智能体");
        }
        int linked = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long aid : validAgentIds) {
            Set<Long> existing = agentSkillMapper.selectList(new LambdaQueryWrapper<AgentSkillEntity>()
                            .eq(AgentSkillEntity::getAgentId, aid))
                    .stream().map(AgentSkillEntity::getSkillId).collect(Collectors.toSet());
            for (Long sid : validSkillIds) {
                if (existing.contains(sid)) continue;
                AgentSkillEntity rel = new AgentSkillEntity();
                rel.setAgentId(aid);
                rel.setSkillId(sid);
                rel.setCreatedAt(now);
                agentSkillMapper.insert(rel);
                linked++;
            }
        }
        return Map.of("ok", true, "linked", linked);
    }

    public List<Map<String, Object>> listRuntimeSkills(AuthPrincipal p, Long runtimeId) {
        RuntimeEntity rt = runtimeMapper.selectById(runtimeId);
        if (rt == null || !Objects.equals(rt.getWorkspaceId(), p.workspaceId())) {
            throw new IllegalArgumentException("运行时不存在");
        }
        return runtimeSkillMapper.selectList(new LambdaQueryWrapper<RuntimeSkillEntity>()
                        .eq(RuntimeSkillEntity::getRuntimeId, runtimeId)
                        .eq(RuntimeSkillEntity::getWorkspaceId, p.workspaceId())
                        .orderByAsc(RuntimeSkillEntity::getName))
                .stream().map(this::runtimeSkillView).collect(Collectors.toList());
    }

    public Map<String, Object> createSkillFromRuntime(AuthPrincipal p, Map<String, Object> body) {
        Long runtimeId = asLong(body.get("runtimeId"));
        Long skillId = asLong(body.get("skillId"));
        if (runtimeId == null || skillId == null) {
            throw new IllegalArgumentException("runtimeId 与 skillId 不能为空");
        }
        RuntimeSkillEntity rs = runtimeSkillMapper.selectById(skillId);
        if (rs == null || !Objects.equals(rs.getWorkspaceId(), p.workspaceId())
                || !Objects.equals(rs.getRuntimeId(), runtimeId)) {
            throw new IllegalArgumentException("运行时 skill 不存在");
        }
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("name", rs.getName());
        createBody.put("description", rs.getDescription());
        createBody.put("content", rs.getContent());
        createBody.put("sourceType", "runtime");
        createBody.put("sourceRef", rs.getSourcePath());
        return createSkill(p, createBody);
    }

    @Transactional
    public Map<String, Object> reportRuntimeSkills(AuthPrincipal daemon, Map<String, Object> body) {
        Long runtimeId = asLong(body.get("runtimeId"));
        if (runtimeId == null) throw new IllegalArgumentException("runtimeId 不能为空");
        RuntimeEntity rt = runtimeMapper.selectById(runtimeId);
        if (rt == null || !Objects.equals(rt.getWorkspaceId(), daemon.workspaceId())) {
            throw new IllegalArgumentException("运行时不存在");
        }
        String daemonId = str(body.get("daemonId"));
        if (!StringUtils.hasText(daemonId)) daemonId = rt.getDaemonId() == null ? "" : rt.getDaemonId();

        List<Map<String, Object>> items = new java.util.ArrayList<>();
        if (body.get("skills") instanceof List<?> list) {
            for (Object x : list) {
                if (!(x instanceof Map<?, ?> raw)) continue;
                Map<String, Object> m = new HashMap<>();
                raw.forEach((k, v) -> m.put(String.valueOf(k), v));
                items.add(m);
            }
        }

        runtimeSkillMapper.delete(new LambdaQueryWrapper<RuntimeSkillEntity>()
                .eq(RuntimeSkillEntity::getRuntimeId, runtimeId));

        LocalDateTime now = LocalDateTime.now();
        int n = 0;
        for (Map<String, Object> item : items) {
            String content = str(item.get("content"));
            String sourcePath = str(item.get("sourcePath"));
            if (!StringUtils.hasText(content) || !StringUtils.hasText(sourcePath)) continue;
            SkillMarkdown.Parsed parsed = SkillMarkdown.parse(content);
            String name = str(item.get("name"));
            if (!StringUtils.hasText(name)) name = parsed.name();
            if (!StringUtils.hasText(name)) {
                name = basenameParent(sourcePath);
            }
            String description = str(item.get("description"));
            if (!StringUtils.hasText(description)) description = parsed.description();
            String hash = str(item.get("contentHash"));
            RuntimeSkillEntity e = new RuntimeSkillEntity();
            e.setWorkspaceId(daemon.workspaceId());
            e.setRuntimeId(runtimeId);
            e.setDaemonId(daemonId);
            e.setName(name);
            e.setDescription(StringUtils.hasText(description) ? description : null);
            e.setContent(content);
            e.setSourcePath(sourcePath);
            e.setContentHash(hash);
            e.setReportedAt(now);
            e.setDeleted(0);
            runtimeSkillMapper.insert(e);
            n++;
        }
        return Map.of("ok", true, "count", n);
    }

    private static String basenameParent(String sourcePath) {
        String p = sourcePath.replace('\\', '/');
        int fileSlash = p.lastIndexOf('/');
        String dir = fileSlash > 0 ? p.substring(0, fileSlash) : p;
        int slash2 = dir.lastIndexOf('/');
        return slash2 >= 0 ? dir.substring(slash2 + 1) : dir;
    }

    public List<SkillEntity> skillsOfAgent(Long agentId) {
        List<Long> ids = agentSkillMapper.selectList(new LambdaQueryWrapper<AgentSkillEntity>()
                        .eq(AgentSkillEntity::getAgentId, agentId))
                .stream().map(AgentSkillEntity::getSkillId).toList();
        if (ids.isEmpty()) return List.of();
        return skillMapper.selectBatchIds(ids);
    }

    private SkillEntity requireSkill(AuthPrincipal p, Long id) {
        SkillEntity s = skillMapper.selectById(id);
        if (s == null || !Objects.equals(s.getWorkspaceId(), p.workspaceId())) {
            throw new IllegalArgumentException("Skill 不存在");
        }
        return s;
    }

    private Map<String, Object> skillView(SkillEntity s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(s.getId()));
        m.put("name", s.getName());
        m.put("description", s.getDescription() == null ? "" : s.getDescription());
        m.put("content", s.getContent());
        List<Map<String, Object>> files = parseSkillFiles(s.getFilesJson());
        m.put("files", files);
        m.put("fileCount", 1 + files.size());
        m.put("sourceType", s.getSourceType() == null ? "manual" : s.getSourceType());
        m.put("sourceRef", s.getSourceRef() == null ? "" : s.getSourceRef());
        m.put("createdByUserId", s.getCreatedByUserId() == null ? null : String.valueOf(s.getCreatedByUserId()));
        m.put("createdBy", "");
        m.put("agentCount", 0);
        m.put("agents", List.of());
        m.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().toString());
        m.put("updatedAt", s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString());
        return m;
    }

    private List<Map<String, Object>> enrichSkillViews(AuthPrincipal p, List<SkillEntity> skills, boolean withContent) {
        if (skills == null || skills.isEmpty()) return List.of();
        List<Long> skillIds = skills.stream().map(SkillEntity::getId).toList();
        List<AgentSkillEntity> links = agentSkillMapper.selectList(new LambdaQueryWrapper<AgentSkillEntity>()
                .in(AgentSkillEntity::getSkillId, skillIds));
        Map<Long, List<Long>> skillToAgents = new HashMap<>();
        Set<Long> agentIdSet = new HashSet<>();
        for (AgentSkillEntity link : links) {
            skillToAgents.computeIfAbsent(link.getSkillId(), k -> new ArrayList<>()).add(link.getAgentId());
            agentIdSet.add(link.getAgentId());
        }
        Map<Long, AgentEntity> agents = agentIdSet.isEmpty()
                ? Map.of()
                : agentMapper.selectBatchIds(agentIdSet).stream()
                        .filter(a -> Objects.equals(a.getWorkspaceId(), p.workspaceId()))
                        .collect(Collectors.toMap(AgentEntity::getId, a -> a, (a, b) -> a));
        Set<Long> userIds = skills.stream()
                .map(SkillEntity::getCreatedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserEntity> users = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(UserEntity::getId, u -> u, (a, b) -> a));

        List<Map<String, Object>> out = new ArrayList<>(skills.size());
        for (SkillEntity s : skills) {
            Map<String, Object> m = skillView(s);
            if (!withContent) {
                m.put("content", "");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> files = (List<Map<String, Object>>) m.get("files");
                if (files != null) {
                    for (Map<String, Object> f : files) {
                        f.put("content", "");
                    }
                }
            }
            UserEntity creator = s.getCreatedByUserId() == null ? null : users.get(s.getCreatedByUserId());
            if (creator != null) {
                m.put("createdBy", StringUtils.hasText(creator.getDisplayName())
                        ? creator.getDisplayName()
                        : creator.getEmail());
            }
            List<Long> aids = skillToAgents.getOrDefault(s.getId(), List.of());
            List<Map<String, Object>> agentBriefs = new ArrayList<>();
            for (Long aid : aids) {
                AgentEntity a = agents.get(aid);
                if (a == null) continue;
                Map<String, Object> brief = new HashMap<>();
                brief.put("id", String.valueOf(a.getId()));
                brief.put("name", a.getName());
                brief.put("description", a.getDescription() == null ? "" : a.getDescription());
                brief.put("avatar", a.getAvatar() == null ? "" : a.getAvatar());
                brief.put("provider", a.getProvider());
                agentBriefs.add(brief);
            }
            m.put("agentCount", agentBriefs.size());
            m.put("agents", agentBriefs);
            out.add(m);
        }
        return out;
    }

    private static List<Long> asLongList(Object raw) {
        List<Long> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object o : list) {
            Long id = asLong(o);
            if (id != null) out.add(id);
        }
        return out;
    }

    private Map<String, Object> runtimeSkillView(RuntimeSkillEntity s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(s.getId()));
        m.put("name", s.getName());
        m.put("description", s.getDescription() == null ? "" : s.getDescription());
        m.put("sourcePath", s.getSourcePath());
        m.put("contentHash", s.getContentHash() == null ? "" : s.getContentHash());
        m.put("reportedAt", s.getReportedAt() == null ? null : s.getReportedAt().toString());
        return m;
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
        String status = normalizeProjectStatus(str(body.get("status")));
        String priority = normalizeProjectPriority(str(body.get("priority")));
        Long assigneeId = asLong(body.get("assigneeUserId"));
        if (assigneeId != null) {
            // 须为当前工作区成员；校验在 Auth 层无注入时仅检查非空用户
            // 轻量：允许指向任意存在用户，UI 只展示成员
        }
        ProjectEntity pr = new ProjectEntity();
        pr.setWorkspaceId(p.workspaceId());
        pr.setName(require(body.get("name"), "项目标题不能为空"));
        String description = str(body.get("description"));
        pr.setDescription(StringUtils.hasText(description) ? description : null);
        pr.setStatus(status);
        pr.setPriority(priority);
        pr.setAssigneeUserId(assigneeId);
        pr.setLocalPath(StringUtils.hasText(localPath) ? localPath : null);
        String repoUrl = str(body.get("repoUrl"));
        pr.setRepoUrl(StringUtils.hasText(repoUrl) ? repoUrl : null);
        pr.setStartDate(asDate(body.get("startDate")));
        pr.setDueDate(asDate(body.get("dueDate")));
        pr.setCreatedAt(LocalDateTime.now());
        pr.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(pr);
        return projectView(pr);
    }

    @Transactional
    public void deleteProject(AuthPrincipal p, Long id) {
        ProjectEntity pr = requireProject(p, id);
        projectMapper.deleteById(pr.getId());
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
        m.put("description", pr.getDescription() == null ? "" : pr.getDescription());
        m.put("status", pr.getStatus() == null ? "planned" : pr.getStatus());
        m.put("priority", pr.getPriority() == null ? "none" : pr.getPriority());
        m.put("assigneeUserId", pr.getAssigneeUserId() == null ? null : String.valueOf(pr.getAssigneeUserId()));
        m.put("localPath", pr.getLocalPath());
        m.put("repoUrl", pr.getRepoUrl() == null ? "" : pr.getRepoUrl());
        m.put("startDate", pr.getStartDate() == null ? null : pr.getStartDate().toString());
        m.put("dueDate", pr.getDueDate() == null ? null : pr.getDueDate().toString());
        m.put("createdAt", pr.getCreatedAt() == null ? null : pr.getCreatedAt().toString());
        m.put("updatedAt", pr.getUpdatedAt() == null ? null : pr.getUpdatedAt().toString());
        return m;
    }

    private static String normalizeProjectStatus(String raw) {
        if (!StringUtils.hasText(raw)) return "planned";
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "planned", "in_progress", "completed", "canceled" -> s;
            case "planning", "plan" -> "planned";
            case "doing", "active" -> "in_progress";
            case "done" -> "completed";
            case "cancelled" -> "canceled";
            default -> throw new IllegalArgumentException("无效的项目状态");
        };
    }

    private static String normalizeProjectPriority(String raw) {
        if (!StringUtils.hasText(raw)) return "none";
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "none", "low", "medium", "high", "urgent" -> s;
            case "no", "unset" -> "none";
            default -> throw new IllegalArgumentException("无效的优先级");
        };
    }

    private static java.time.LocalDate asDate(Object v) {
        if (v == null || "".equals(v)) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        // 允许 yyyy-MM-dd 或 ISO datetime
        if (s.length() >= 10) s = s.substring(0, 10);
        return java.time.LocalDate.parse(s);
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
        if (!protocolService.isAllowedProvider(workspaceId, provider)) {
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
        archiveAgentsBoundToRuntime(p, r.getId());
        runtimeMapper.deleteById(id);
        wsHub.publish(p.workspaceId(), Map.of("type", "runtime.deleted", "id", String.valueOf(id)));
    }

    /**
     * 删除运行时前：归档绑定该 runtime 的智能体，并取消其排队中/运行中的 task。
     */
    private void archiveAgentsBoundToRuntime(AuthPrincipal p, Long runtimeId) {
        if (runtimeId == null) return;
        List<AgentEntity> agents = agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getWorkspaceId, p.workspaceId())
                .eq(AgentEntity::getRuntimeId, runtimeId)
                .and(w -> w.isNull(AgentEntity::getStatus)
                        .or()
                        .ne(AgentEntity::getStatus, "archived")));
        LocalDateTime now = LocalDateTime.now();
        for (AgentEntity a : agents) {
            a.setStatus("archived");
            a.setUpdatedAt(now);
            agentMapper.updateById(a);
            List<TaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                    .eq(TaskEntity::getWorkspaceId, p.workspaceId())
                    .eq(TaskEntity::getAgentId, a.getId()));
            for (TaskEntity t : tasks) {
                if (TaskStatuses.isTerminal(t.getStatus())) continue;
                t.setStatus(TaskStatuses.CANCELLED);
                t.setFinishedAt(now);
                t.setUpdatedAt(now);
                taskMapper.updateById(t);
                wsHub.publish(p.workspaceId(), Map.of("type", "task.updated", "task", Map.of(
                        "id", String.valueOf(t.getId()),
                        "agentId", String.valueOf(t.getAgentId()),
                        "status", t.getStatus()
                )));
            }
            wsHub.publish(p.workspaceId(), Map.of("type", "agent.updated", "agent", agentView(a)));
        }
    }

    /**
     * 会话侧添加运行时：写入当前登录用户工作区。
     * daemonId 建议传 Desktop profile 的稳定实例 ID，便于随后由 Desktop Daemon 接管心跳。
     */
    @Transactional
    public Map<String, Object> addRuntimeForSession(AuthPrincipal p, String provider, String hostName,
                                                    String daemonId) {
        if (!protocolService.isAllowedProvider(p.workspaceId(), provider)) {
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
            archiveAgentsBoundToRuntime(p, r.getId());
            runtimeMapper.deleteById(r.getId());
            wsHub.publish(p.workspaceId(), Map.of("type", "runtime.deleted", "id", String.valueOf(r.getId())));
        }
    }

    /**
     * 停用/删除协议前：若仍有未归档智能体绑定该协议（或以其为 base），则拒绝。
     */
    public void assertNoActiveAgentsUsingProtocol(AuthPrincipal p, String protocolCode) {
        if (p.workspaceId() == null || !StringUtils.hasText(protocolCode)) return;
        String code = protocolCode.toLowerCase().trim();

        Set<Long> runtimeIds = new HashSet<>();
        List<RuntimeEntity> runtimes = runtimeMapper.selectList(new LambdaQueryWrapper<RuntimeEntity>()
                .eq(RuntimeEntity::getWorkspaceId, p.workspaceId()));
        for (RuntimeEntity r : runtimes) {
            String provider = r.getProvider() == null ? "" : r.getProvider().toLowerCase();
            String base = protocolService.baseProvider(p.workspaceId(), provider);
            if (code.equals(provider) || code.equals(base)) {
                runtimeIds.add(r.getId());
            }
        }

        List<AgentEntity> agents = agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getWorkspaceId, p.workspaceId())
                .and(w -> w.isNull(AgentEntity::getStatus)
                        .or()
                        .ne(AgentEntity::getStatus, "archived")));

        List<String> names = new ArrayList<>();
        for (AgentEntity a : agents) {
            String provider = a.getProvider() == null ? "" : a.getProvider().toLowerCase();
            String base = protocolService.baseProvider(p.workspaceId(), provider);
            boolean byProvider = code.equals(provider) || code.equals(base);
            boolean byRuntime = a.getRuntimeId() != null && runtimeIds.contains(a.getRuntimeId());
            if (byProvider || byRuntime) {
                names.add(StringUtils.hasText(a.getName()) ? a.getName() : String.valueOf(a.getId()));
            }
        }
        if (names.isEmpty()) return;

        String sample = names.stream().limit(3).collect(Collectors.joining("、"));
        String more = names.size() > 3 ? " 等" : "";
        throw new IllegalArgumentException(
                "仍有 " + names.size() + " 个智能体在使用该协议（" + sample + more
                        + "）。请先归档或删除这些智能体后再停用协议。");
    }

    /**
     * 协议停用/删除后：移除该协议及以其为 base 的自定义运行时。
     */
    @Transactional
    public void purgeRuntimesForProtocol(AuthPrincipal p, String protocolCode) {
        if (p.workspaceId() == null || !StringUtils.hasText(protocolCode)) return;
        String code = protocolCode.toLowerCase().trim();
        List<RuntimeEntity> list = runtimeMapper.selectList(new LambdaQueryWrapper<RuntimeEntity>()
                .eq(RuntimeEntity::getWorkspaceId, p.workspaceId()));
        for (RuntimeEntity r : list) {
            String provider = r.getProvider() == null ? "" : r.getProvider().toLowerCase();
            String base = protocolService.baseProvider(p.workspaceId(), provider);
            if (!code.equals(provider) && !code.equals(base)) {
                continue;
            }
            archiveAgentsBoundToRuntime(p, r.getId());
            runtimeMapper.deleteById(r.getId());
            wsHub.publish(p.workspaceId(), Map.of("type", "runtime.deleted", "id", String.valueOf(r.getId())));
        }
    }

    public void heartbeat(Long runtimeId) {
        RuntimeEntity r = runtimeMapper.selectById(runtimeId);
        if (r == null) return;
        // 协议已停用则不再保活，并删除残留运行时
        if (!protocolService.isAllowedProvider(r.getWorkspaceId(), r.getProvider())) {
            AuthPrincipal ctx = new AuthPrincipal(0L, "", "daemon", r.getWorkspaceId());
            archiveAgentsBoundToRuntime(ctx, r.getId());
            runtimeMapper.deleteById(r.getId());
            wsHub.publish(r.getWorkspaceId(), Map.of("type", "runtime.deleted", "id", String.valueOf(r.getId())));
            return;
        }
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
        String profile = "";
        String kind = "builtin";
        String displayName = "";
        String command = "";
        String description = "";
        String baseProvider = protocolService.baseProvider(r.getWorkspaceId(), r.getProvider());
        if (StringUtils.hasText(r.getMetaJson())) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(r.getMetaJson(), Map.class);
                if (meta.get("profile") != null) profile = String.valueOf(meta.get("profile"));
                if (meta.get("kind") != null) kind = String.valueOf(meta.get("kind"));
                if (meta.get("displayName") != null) displayName = String.valueOf(meta.get("displayName"));
                if (meta.get("command") != null) command = String.valueOf(meta.get("command"));
                if (meta.get("description") != null) description = String.valueOf(meta.get("description"));
                if (meta.get("baseProvider") != null) baseProvider = String.valueOf(meta.get("baseProvider"));
            } catch (Exception ignored) {
                /* ignore */
            }
        }
        if (r.getProvider() != null && r.getProvider().startsWith("custom_")) {
            kind = "custom";
        }
        m.put("profile", profile);
        m.put("kind", kind);
        m.put("displayName", displayName);
        m.put("command", command);
        m.put("description", description);
        m.put("baseProvider", baseProvider);
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

    private static final com.fasterxml.jackson.databind.ObjectMapper SKILL_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static List<Map<String, Object>> parseSkillFiles(String raw) {
        if (!StringUtils.hasText(raw)) return new ArrayList<>();
        try {
            List<Map<String, Object>> list = SKILL_JSON.readValue(
                    raw,
                    SKILL_JSON.getTypeFactory().constructCollectionType(List.class, Map.class));
            return normalizeSkillFiles(list);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static List<Map<String, Object>> normalizeSkillFiles(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        Set<String> seen = new HashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            String path = str(m.get("path")).replace('\\', '/').trim();
            if (!StringUtils.hasText(path)) continue;
            if (path.startsWith("/")) path = path.substring(1);
            if (path.contains("..")) throw new IllegalArgumentException("非法文件路径: " + path);
            String lower = path.toLowerCase();
            if ("skill.md".equals(lower)) {
                throw new IllegalArgumentException("SKILL.md 请使用主文件编辑，不能作为附属文件");
            }
            if (!seen.add(lower)) throw new IllegalArgumentException("重复文件路径: " + path);
            Object contentObj = m.get("content");
            String content = contentObj == null ? "" : String.valueOf(contentObj);
            if (content.length() > 2_000_000) {
                throw new IllegalArgumentException("附属文件过大: " + path);
            }
            Map<String, Object> row = new HashMap<>();
            row.put("path", path);
            row.put("content", content);
            out.add(row);
            if (out.size() > 100) throw new IllegalArgumentException("附属文件过多");
        }
        return out;
    }

    private static String serializeSkillFiles(List<Map<String, Object>> files) {
        if (files == null || files.isEmpty()) return null;
        try {
            return SKILL_JSON.writeValueAsString(files);
        } catch (Exception e) {
            throw new IllegalArgumentException("附属文件序列化失败");
        }
    }

    private static String normalizeModel(Object raw) {
        String s = str(raw);
        if (!StringUtils.hasText(s)) return "default";
        if (s.length() > 64) throw new IllegalArgumentException("模型标识过长");
        return s;
    }

    private static String normalizeThinkingMode(Object raw) {
        String s = str(raw).toLowerCase();
        if (!StringUtils.hasText(s)) return "cli";
        if (!"cli".equals(s) && !"low".equals(s) && !"medium".equals(s) && !"high".equals(s)) {
            throw new IllegalArgumentException("不支持的思考强度");
        }
        return s;
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
