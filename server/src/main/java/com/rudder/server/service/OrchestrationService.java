package com.rudder.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rudder.server.auth.AuthPrincipal;
import com.rudder.server.domain.AgentEntity;
import com.rudder.server.domain.ChatMessageEntity;
import com.rudder.server.domain.ChatSessionEntity;
import com.rudder.server.domain.IssueCommentEntity;
import com.rudder.server.domain.IssueEntity;
import com.rudder.server.domain.ProjectEntity;
import com.rudder.server.domain.RuntimeEntity;
import com.rudder.server.domain.SkillEntity;
import com.rudder.server.domain.TaskEntity;
import com.rudder.server.mapper.ChatMessageMapper;
import com.rudder.server.mapper.ChatSessionMapper;
import com.rudder.server.mapper.IssueCommentMapper;
import com.rudder.server.mapper.IssueMapper;
import com.rudder.server.mapper.RuntimeMapper;
import com.rudder.server.mapper.TaskMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Task / Chat / Issue 核心编排。 */
@Service
@RequiredArgsConstructor
public class OrchestrationService {

    private static final Pattern MENTION = Pattern.compile("@([^\\s@]+)");

    private final TaskMapper taskMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final IssueMapper issueMapper;
    private final IssueCommentMapper issueCommentMapper;
    private final RuntimeMapper runtimeMapper;
    private final ResourceService resourceService;
    private final ProtocolService protocolService;
    private final NettyWsHub wsHub;

    // ===== Chat =====
    public List<Map<String, Object>> listSessions(AuthPrincipal p) {
        return chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getWorkspaceId, p.workspaceId())
                        .orderByDesc(ChatSessionEntity::getUpdatedAt))
                .stream().map(s -> {
                    AgentEntity agent = resourceService.requireAgent(p, s.getAgentId());
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", String.valueOf(s.getId()));
                    m.put("title", s.getTitle());
                    m.put("agentId", String.valueOf(s.getAgentId()));
                    m.put("agentName", agent.getName());
                    m.put("projectId", s.getProjectId() == null ? null : String.valueOf(s.getProjectId()));
                    m.put("updatedAt", s.getUpdatedAt().toString());
                    return m;
                }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createSession(AuthPrincipal p, Map<String, Object> body) {
        Long agentId = asLong(body.get("agentId"));
        if (agentId == null) throw new IllegalArgumentException("必须选择 Agent");
        AgentEntity agent = resourceService.requireAgent(p, agentId);
        resourceService.assertAgentActive(agent);
        Long projectId = asLong(body.get("projectId"));
        if (projectId != null) resourceService.requireProject(p, projectId);

        ChatSessionEntity s = new ChatSessionEntity();
        s.setWorkspaceId(p.workspaceId());
        s.setAgentId(agentId);
        s.setProjectId(projectId);
        s.setTitle("与 " + agent.getName() + " 的对话");
        s.setCreatedBy(p.userId());
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(s);

        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(s.getId()));
        m.put("title", s.getTitle());
        m.put("agentId", String.valueOf(agent.getId()));
        m.put("agentName", agent.getName());
        m.put("projectId", projectId == null ? null : String.valueOf(projectId));
        return m;
    }

    public Map<String, Object> getSession(AuthPrincipal p, Long id) {
        ChatSessionEntity s = requireSession(p, id);
        AgentEntity agent = resourceService.requireAgent(p, s.getAgentId());
        List<Map<String, Object>> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, id)
                        .orderByAsc(ChatMessageEntity::getId))
                .stream().map(this::msgView).collect(Collectors.toList());
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(s.getId()));
        m.put("title", s.getTitle());
        m.put("agent", Map.of("id", String.valueOf(agent.getId()), "name", agent.getName(), "provider", agent.getProvider()));
        m.put("projectId", s.getProjectId() == null ? null : String.valueOf(s.getProjectId()));
        m.put("messages", messages);
        return m;
    }

    @Transactional
    public Map<String, Object> sendMessage(AuthPrincipal p, Long sessionId, String content) {
        if (!StringUtils.hasText(content)) throw new IllegalArgumentException("消息不能为空");
        ChatSessionEntity s = requireSession(p, sessionId);
        AgentEntity agent = resourceService.requireAgent(p, s.getAgentId());

        ChatMessageEntity userMsg = new ChatMessageEntity();
        userMsg.setSessionId(sessionId);
        userMsg.setWorkspaceId(p.workspaceId());
        userMsg.setRole("user");
        userMsg.setContent(content);
        userMsg.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(userMsg);

        TaskEntity task = enqueueTask(p, agent, "chat", content, s.getProjectId(), null, sessionId, userMsg.getId());
        userMsg.setTaskId(task.getId());
        chatMessageMapper.updateById(userMsg);

        s.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.updateById(s);

        resourceService.notifyUser(p.workspaceId(), p.userId(), "任务已入队", "Chat 消息已派给 " + agent.getName(), "task", task.getId());
        wsHub.publish(p.workspaceId(), Map.of("type", "chat.message", "sessionId", String.valueOf(sessionId), "message", msgView(userMsg)));
        wsHub.publish(p.workspaceId(), Map.of("type", "task.updated", "task", taskView(task)));

        return Map.of("message", msgView(userMsg), "task", taskView(task));
    }

    private ChatSessionEntity requireSession(AuthPrincipal p, Long id) {
        ChatSessionEntity s = chatSessionMapper.selectById(id);
        if (s == null || !Objects.equals(s.getWorkspaceId(), p.workspaceId())) {
            throw new IllegalArgumentException("会话不存在");
        }
        return s;
    }

    // ===== Issue =====
    public List<Map<String, Object>> listIssues(AuthPrincipal p) {
        return issueMapper.selectList(new LambdaQueryWrapper<IssueEntity>()
                        .eq(IssueEntity::getWorkspaceId, p.workspaceId())
                        .orderByDesc(IssueEntity::getId))
                .stream().map(this::issueView).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createIssue(AuthPrincipal p, Map<String, Object> body) {
        IssueEntity issue = new IssueEntity();
        issue.setWorkspaceId(p.workspaceId());
        issue.setTitle(require(body.get("title"), "标题不能为空"));
        issue.setDescription(str(body.get("description")));
        String statusRaw = str(body.get("status"));
        issue.setStatus(StringUtils.hasText(statusRaw) ? IssueStatuses.requireValid(statusRaw) : IssueStatuses.TODO);
        issue.setPriority(StringUtils.hasText(str(body.get("priority"))) ? str(body.get("priority")) : "medium");
        issue.setProjectId(asLong(body.get("projectId")));
        issue.setCreatedBy(p.userId());
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        // 兼容 agentId 简写：等同于 assigneeType=agent + assigneeId
        Object assigneeType = body.get("assigneeType");
        Object assigneeId = body.get("assigneeId");
        if (body.get("agentId") != null && (assigneeId == null || !StringUtils.hasText(str(assigneeId)))) {
            assigneeType = "agent";
            assigneeId = body.get("agentId");
        }
        applyAssignee(p, issue, assigneeType, assigneeId, true);
        issueMapper.insert(issue);
        if ("agent".equals(issue.getAssigneeType()) && issue.getAssigneeId() != null) {
            AgentEntity agent = resourceService.requireAgent(p, issue.getAssigneeId());
            TaskEntity task = enqueueTask(p, agent, "assign",
                    "请处理 Issue：" + issue.getTitle() + "\n" + nullToEmpty(issue.getDescription()),
                    issue.getProjectId(), issue.getId(), null, null);
            wsHub.publish(p.workspaceId(), Map.of("type", "task.updated", "task", taskView(task)));
        }
        return issueView(issue);
    }

    @Transactional
    public Map<String, Object> updateIssue(AuthPrincipal p, Long id, Map<String, Object> body) {
        IssueEntity issue = requireIssue(p, id);
        boolean assigneeChanged = false;
        if (body.containsKey("title")) issue.setTitle(require(body.get("title"), "标题不能为空"));
        if (body.containsKey("description")) issue.setDescription(str(body.get("description")));
        if (body.containsKey("status")) issue.setStatus(IssueStatuses.requireValid(str(body.get("status"))));
        if (body.containsKey("priority")) issue.setPriority(str(body.get("priority")));
        if (body.containsKey("projectId")) issue.setProjectId(asLong(body.get("projectId")));
        if (body.containsKey("assigneeType") || body.containsKey("assigneeId")) {
            Long old = issue.getAssigneeId();
            String oldType = issue.getAssigneeType();
            applyAssignee(p, issue, body.getOrDefault("assigneeType", issue.getAssigneeType()),
                    body.getOrDefault("assigneeId", issue.getAssigneeId()), false);
            assigneeChanged = !Objects.equals(old, issue.getAssigneeId()) || !Objects.equals(oldType, issue.getAssigneeType());
        }
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.updateById(issue);
        if (assigneeChanged && "agent".equals(issue.getAssigneeType()) && issue.getAssigneeId() != null) {
            AgentEntity agent = resourceService.requireAgent(p, issue.getAssigneeId());
            TaskEntity task = enqueueTask(p, agent, "assign",
                    "请处理 Issue：" + issue.getTitle() + "\n" + nullToEmpty(issue.getDescription()),
                    issue.getProjectId(), issue.getId(), null, null);
            wsHub.publish(p.workspaceId(), Map.of("type", "task.updated", "task", taskView(task)));
        }
        return issueView(issue);
    }

    public Map<String, Object> getIssue(AuthPrincipal p, Long id) {
        IssueEntity issue = requireIssue(p, id);
        List<Map<String, Object>> comments = issueCommentMapper.selectList(new LambdaQueryWrapper<IssueCommentEntity>()
                        .eq(IssueCommentEntity::getIssueId, id)
                        .orderByAsc(IssueCommentEntity::getId))
                .stream().map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", String.valueOf(c.getId()));
                    m.put("authorType", c.getAuthorType());
                    m.put("authorId", String.valueOf(c.getAuthorId()));
                    m.put("content", c.getContent());
                    m.put("taskId", c.getTaskId() == null ? null : String.valueOf(c.getTaskId()));
                    m.put("createdAt", c.getCreatedAt().toString());
                    return m;
                }).collect(Collectors.toList());
        List<Map<String, Object>> tasks = taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                        .eq(TaskEntity::getIssueId, id)
                        .orderByDesc(TaskEntity::getId)
                        .last("LIMIT 20"))
                .stream().map(this::taskView).collect(Collectors.toList());
        Map<String, Object> m = issueView(issue);
        m.put("comments", comments);
        m.put("tasks", tasks);
        return m;
    }

    @Transactional
    public Map<String, Object> addComment(AuthPrincipal p, Long issueId, String content) {
        if (!StringUtils.hasText(content)) throw new IllegalArgumentException("评论不能为空");
        IssueEntity issue = requireIssue(p, issueId);
        IssueCommentEntity c = new IssueCommentEntity();
        c.setIssueId(issueId);
        c.setWorkspaceId(p.workspaceId());
        c.setAuthorType("user");
        c.setAuthorId(p.userId());
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now());
        issueCommentMapper.insert(c);

        // @Agent 简写：指派给当前 Issue 已绑定的 Agent；否则按 @名称 匹配
        boolean triggered = false;
        if (content.matches("(?s).*@Agent\\b.*")
                && "agent".equals(issue.getAssigneeType())
                && issue.getAssigneeId() != null) {
            AgentEntity agent = resourceService.requireAgent(p, issue.getAssigneeId());
            TaskEntity task = enqueueTask(p, agent, "mention", content, issue.getProjectId(), issueId, null, null);
            c.setTaskId(task.getId());
            issueCommentMapper.updateById(c);
            wsHub.publish(p.workspaceId(), Map.of("type", "task.updated", "task", taskView(task)));
            triggered = true;
        }
        if (!triggered) {
            Matcher matcher = MENTION.matcher(content);
            while (matcher.find()) {
                String name = matcher.group(1);
                if ("Agent".equalsIgnoreCase(name)) {
                    continue;
                }
                AgentEntity agent = findAgentByName(p, name);
                if (agent != null) {
                    TaskEntity task = enqueueTask(p, agent, "mention", content, issue.getProjectId(), issueId, null, null);
                    c.setTaskId(task.getId());
                    issueCommentMapper.updateById(c);
                    wsHub.publish(p.workspaceId(), Map.of("type", "task.updated", "task", taskView(task)));
                }
            }
        }
        wsHub.publish(p.workspaceId(), Map.of("type", "issue.comment", "issueId", String.valueOf(issueId)));
        return Map.of("id", String.valueOf(c.getId()), "content", c.getContent(),
                "taskId", c.getTaskId() == null ? null : String.valueOf(c.getTaskId()));
    }

    private AgentEntity findAgentByName(AuthPrincipal p, String name) {
        return resourceService.listAgents(p).stream()
                .filter(a -> name.equals(a.get("name")) || name.equals(String.valueOf(a.get("name")).replace(" ", "")))
                .findFirst()
                .map(a -> resourceService.requireAgent(p, Long.parseLong(String.valueOf(a.get("id")))))
                .orElse(null);
    }

    private void applyAssignee(AuthPrincipal p, IssueEntity issue, Object typeObj, Object idObj, boolean creating) {
        String type = str(typeObj);
        Long id = asLong(idObj);
        if (!StringUtils.hasText(type) || id == null) {
            if (!creating) {
                issue.setAssigneeType(null);
                issue.setAssigneeId(null);
            }
            return;
        }
        if ("agent".equals(type)) {
            resourceService.requireAgent(p, id);
        }
        issue.setAssigneeType(type);
        issue.setAssigneeId(id);
    }

    private IssueEntity requireIssue(AuthPrincipal p, Long id) {
        IssueEntity issue = issueMapper.selectById(id);
        if (issue == null || !Objects.equals(issue.getWorkspaceId(), p.workspaceId())) {
            throw new IllegalArgumentException("Issue 不存在");
        }
        return issue;
    }

    // ===== Task =====
    public TaskEntity enqueueTask(AuthPrincipal p, AgentEntity agent, String trigger, String prompt,
                                  Long projectId, Long issueId, Long chatSessionId, Long chatMessageId) {
        resourceService.assertAgentActive(agent);
        TaskEntity task = new TaskEntity();
        task.setWorkspaceId(p.workspaceId());
        task.setAgentId(agent.getId());
        task.setRuntimeId(agent.getRuntimeId());
        task.setProjectId(projectId);
        task.setTriggerSource(trigger);
        task.setStatus("queued");
        task.setPrompt(prompt);
        task.setIssueId(issueId);
        task.setChatSessionId(chatSessionId);
        task.setChatMessageId(chatMessageId);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    public List<Map<String, Object>> listTasks(AuthPrincipal p) {
        return taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                        .eq(TaskEntity::getWorkspaceId, p.workspaceId())
                        .orderByDesc(TaskEntity::getId)
                        .last("LIMIT 50"))
                .stream().map(this::taskView).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> cancelTask(AuthPrincipal p, Long id) {
        TaskEntity task = requireTask(p, id);
        if (TaskStatuses.isTerminal(task.getStatus())) {
            throw new IllegalArgumentException("任务已结束，无法取消");
        }
        task.setStatus("cancelled");
        task.setFinishedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        wsHub.publish(p.workspaceId(), Map.of("type", "task.updated", "task", taskView(task)));
        return taskView(task);
    }

    @Transactional
    public Map<String, Object> rerunTask(AuthPrincipal p, Long id) {
        TaskEntity old = requireTask(p, id);
        AgentEntity agent = resourceService.requireAgent(p, old.getAgentId());
        TaskEntity task = enqueueTask(p, agent, "rerun", old.getPrompt(), old.getProjectId(),
                old.getIssueId(), old.getChatSessionId(), old.getChatMessageId());
        wsHub.publish(p.workspaceId(), Map.of("type", "task.updated", "task", taskView(task)));
        return taskView(task);
    }

    private TaskEntity requireTask(AuthPrincipal p, Long id) {
        TaskEntity task = taskMapper.selectById(id);
        if (task == null || !Objects.equals(task.getWorkspaceId(), p.workspaceId())) {
            throw new IllegalArgumentException("任务不存在");
        }
        return task;
    }

    // ===== Daemon APIs =====
    @Transactional
    public Map<String, Object> claimTask(AuthPrincipal daemon, Long runtimeId) {
        RuntimeEntity runtime = runtimeMapper.selectById(runtimeId);
        if (runtime == null || !Objects.equals(runtime.getWorkspaceId(), daemon.workspaceId())) {
            throw new IllegalArgumentException("Runtime 不存在");
        }
        resourceService.heartbeat(runtimeId);

        // 优先领取绑定该 runtime 的任务，其次领取同 provider 且 runtime 为空的任务
        List<TaskEntity> candidates = taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getWorkspaceId, daemon.workspaceId())
                .eq(TaskEntity::getStatus, "queued")
                .and(w -> w.eq(TaskEntity::getRuntimeId, runtimeId)
                        .or().isNull(TaskEntity::getRuntimeId))
                .orderByAsc(TaskEntity::getId)
                .last("LIMIT 20"));
        TaskEntity task = null;
        AgentEntity agent = null;
        AuthPrincipal asUser = new AuthPrincipal(daemon.userId(), daemon.email(), "session", daemon.workspaceId());
        for (TaskEntity candidate : candidates) {
            AgentEntity a;
            try {
                a = resourceService.requireAgent(asUser, candidate.getAgentId());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            // 已归档：跳过领取，保留 task 供恢复后继续
            if ("archived".equalsIgnoreCase(a.getStatus())) {
                continue;
            }
            task = candidate;
            agent = a;
            break;
        }
        if (task == null || agent == null) {
            return Map.of("task", (Object) null);
        }
        // 已绑定其它 runtime 的任务不领；未绑定则要求 provider 匹配（stub 例外）
        if (task.getRuntimeId() != null && !Objects.equals(task.getRuntimeId(), runtimeId)) {
            return Map.of("task", (Object) null);
        }
        boolean providerMatch = agent.getProvider().equals(runtime.getProvider())
                || protocolService.baseProvider(daemon.workspaceId(), agent.getProvider())
                        .equals(protocolService.baseProvider(daemon.workspaceId(), runtime.getProvider()));
        if (task.getRuntimeId() == null
                && !"stub".equals(runtime.getProvider())
                && !providerMatch) {
            return Map.of("task", (Object) null);
        }
        task.setRuntimeId(runtimeId);
        task.setStatus("dispatched");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        ProjectEntity project = null;
        if (task.getProjectId() != null) {
            project = resourceService.requireProject(
                    new AuthPrincipal(daemon.userId(), daemon.email(), "session", daemon.workspaceId()),
                    task.getProjectId());
        }
        String workDir = WorkdirResolver.resolve(task.getWorkspaceId(), task.getId(),
                project == null ? null : project.getLocalPath());
        task.setWorkDir(workDir);
        taskMapper.updateById(task);

        List<SkillEntity> skills = resourceService.skillsOfAgent(agent.getId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("task", taskView(task));
        Map<String, Object> agentPayload = new HashMap<>();
        agentPayload.put("id", String.valueOf(agent.getId()));
        agentPayload.put("name", agent.getName());
        agentPayload.put("provider", agent.getProvider());
        agentPayload.put("instructions", nullToEmpty(agent.getInstructions()));
        agentPayload.put("model", StringUtils.hasText(agent.getModel()) ? agent.getModel() : "default");
        agentPayload.put("thinkingMode", StringUtils.hasText(agent.getThinkingMode()) ? agent.getThinkingMode() : "cli");
        agentPayload.put("maxConcurrency", agent.getMaxConcurrency() == null ? 1 : agent.getMaxConcurrency());
        payload.put("agent", agentPayload);
        payload.put("workDir", workDir);
        payload.put("envRoot", WorkdirResolver.sandboxEnvRoot(task.getWorkspaceId(), task.getId()));
        payload.put("skills", skills.stream().map(s -> Map.of("name", s.getName(), "content", s.getContent())).toList());
        payload.put("localPathMode", project != null && StringUtils.hasText(project.getLocalPath()));
        // 自定义运行时命令（供 Daemon 直接 exec）
        if (StringUtils.hasText(runtime.getMetaJson()) && runtime.getMetaJson().contains("command")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(runtime.getMetaJson(), Map.class);
                if (meta.get("command") != null) {
                    payload.put("command", String.valueOf(meta.get("command")));
                }
                if (meta.get("baseProvider") != null) {
                    payload.put("baseProvider", String.valueOf(meta.get("baseProvider")));
                }
            } catch (Exception ignored) {
                /* ignore */
            }
        }
        return payload;
    }

    @Transactional
    public void reportTask(AuthPrincipal daemon, Long taskId, Map<String, Object> body) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getWorkspaceId(), daemon.workspaceId())) {
            throw new IllegalArgumentException("任务不存在");
        }
        String status = str(body.get("status"));
        if ("running".equals(status)) {
            task.setStatus("running");
            task.setStartedAt(LocalDateTime.now());
        } else if ("completed".equals(status)) {
            task.setStatus("completed");
            task.setResultSummary(str(body.get("resultSummary")));
            task.setFinishedAt(LocalDateTime.now());
            postResult(task, true);
        } else if ("failed".equals(status)) {
            task.setStatus("failed");
            task.setErrorMessage(str(body.get("errorMessage")));
            task.setFinishedAt(LocalDateTime.now());
            postResult(task, false);
        } else if ("log".equals(status)) {
            wsHub.publish(daemon.workspaceId(), Map.of(
                    "type", "task.log",
                    "taskId", String.valueOf(taskId),
                    "line", str(body.get("line"))
            ));
            return;
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        wsHub.publish(daemon.workspaceId(), Map.of("type", "task.updated", "task", taskView(task)));
    }

    private void postResult(TaskEntity task, boolean ok) {
        String content = ok ? ("【Agent 完成】\n" + nullToEmpty(task.getResultSummary()))
                : ("【Agent 失败】\n" + nullToEmpty(task.getErrorMessage()));
        Long notifyUserId = null;
        if (task.getChatSessionId() != null) {
            ChatMessageEntity msg = new ChatMessageEntity();
            msg.setSessionId(task.getChatSessionId());
            msg.setWorkspaceId(task.getWorkspaceId());
            msg.setRole("assistant");
            msg.setContent(content);
            msg.setTaskId(task.getId());
            msg.setCreatedAt(LocalDateTime.now());
            chatMessageMapper.insert(msg);
            wsHub.publish(task.getWorkspaceId(), Map.of(
                    "type", "chat.message",
                    "sessionId", String.valueOf(task.getChatSessionId()),
                    "message", msgView(msg)
            ));
            ChatSessionEntity session = chatSessionMapper.selectById(task.getChatSessionId());
            if (session != null) {
                notifyUserId = session.getCreatedBy();
            }
        }
        if (task.getIssueId() != null) {
            IssueCommentEntity c = new IssueCommentEntity();
            c.setIssueId(task.getIssueId());
            c.setWorkspaceId(task.getWorkspaceId());
            c.setAuthorType("agent");
            c.setAuthorId(task.getAgentId());
            c.setContent(content);
            c.setTaskId(task.getId());
            c.setCreatedAt(LocalDateTime.now());
            issueCommentMapper.insert(c);
            wsHub.publish(task.getWorkspaceId(), Map.of("type", "issue.comment", "issueId", String.valueOf(task.getIssueId())));
            IssueEntity issue = issueMapper.selectById(task.getIssueId());
            if (issue != null) {
                notifyUserId = issue.getCreatedBy();
            }
        }
        if (notifyUserId != null) {
            resourceService.notifyUser(
                    task.getWorkspaceId(),
                    notifyUserId,
                    ok ? "任务已完成" : "任务失败",
                    content.length() > 200 ? content.substring(0, 200) + "…" : content,
                    "task",
                    task.getId()
            );
        }
    }

    public Map<String, Object> taskView(TaskEntity t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(t.getId()));
        m.put("agentId", String.valueOf(t.getAgentId()));
        m.put("status", t.getStatus());
        m.put("triggerSource", t.getTriggerSource());
        m.put("prompt", t.getPrompt());
        m.put("resultSummary", t.getResultSummary());
        m.put("errorMessage", t.getErrorMessage());
        m.put("workDir", t.getWorkDir());
        m.put("issueId", t.getIssueId() == null ? null : String.valueOf(t.getIssueId()));
        m.put("chatSessionId", t.getChatSessionId() == null ? null : String.valueOf(t.getChatSessionId()));
        return m;
    }

    private Map<String, Object> msgView(ChatMessageEntity m) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", String.valueOf(m.getId()));
        map.put("role", m.getRole());
        map.put("content", m.getContent());
        map.put("taskId", m.getTaskId() == null ? null : String.valueOf(m.getTaskId()));
        map.put("createdAt", m.getCreatedAt().toString());
        return map;
    }

    private Map<String, Object> issueView(IssueEntity i) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(i.getId()));
        m.put("title", i.getTitle());
        m.put("description", i.getDescription());
        String status = IssueStatuses.normalize(i.getStatus());
        m.put("status", status != null ? status : IssueStatuses.TODO);
        m.put("priority", i.getPriority());
        m.put("assigneeType", i.getAssigneeType());
        m.put("assigneeId", i.getAssigneeId() == null ? null : String.valueOf(i.getAssigneeId()));
        m.put("projectId", i.getProjectId() == null ? null : String.valueOf(i.getProjectId()));
        m.put("createdAt", i.getCreatedAt() == null ? null : i.getCreatedAt().toString());
        m.put("updatedAt", i.getUpdatedAt() == null ? null : i.getUpdatedAt().toString());
        return m;
    }

    private static String require(Object v, String msg) {
        String s = str(v);
        if (!StringUtils.hasText(s)) throw new IllegalArgumentException(msg);
        return s;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Long asLong(Object v) {
        if (v == null || "".equals(v)) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }
}
