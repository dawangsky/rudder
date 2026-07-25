package com.rudder.server.web;

import com.rudder.server.auth.AuthContext;
import com.rudder.server.auth.AuthPrincipal;
import com.rudder.server.domain.TokenTypes;
import com.rudder.server.service.OrchestrationService;
import com.rudder.server.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 业务 REST：Agent/Skill/Project/Runtime/Chat/Issue/Task/Inbox。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiControllers {

    private final ResourceService resourceService;
    private final OrchestrationService orchestrationService;

    private AuthPrincipal session() {
        AuthPrincipal p = AuthContext.get();
        if (p == null || !TokenTypes.SESSION.equals(p.tokenType())) {
            throw new IllegalArgumentException("需要会话登录");
        }
        return p;
    }

    private AuthPrincipal daemon() {
        AuthPrincipal p = AuthContext.get();
        if (p == null || !TokenTypes.DAEMON.equals(p.tokenType())) {
            throw new IllegalArgumentException("需要 Daemon Token");
        }
        return p;
    }

    // Agent
    @GetMapping("/agents")
    public List<Map<String, Object>> agents() { return resourceService.listAgents(session()); }

    @PostMapping("/agents")
    public Map<String, Object> createAgent(@RequestBody Map<String, Object> body) {
        return resourceService.createAgent(session(), body);
    }

    @PutMapping("/agents/{id}")
    public Map<String, Object> updateAgent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return resourceService.updateAgent(session(), id, body);
    }

    @DeleteMapping("/agents/{id}")
    public Map<String, Object> deleteAgent(@PathVariable Long id) {
        resourceService.deleteAgent(session(), id);
        return Map.of("ok", true);
    }

    // Skill
    @GetMapping("/skills")
    public List<Map<String, Object>> skills() { return resourceService.listSkills(session()); }

    @PostMapping("/skills")
    public Map<String, Object> createSkill(@RequestBody Map<String, Object> body) {
        return resourceService.createSkill(session(), body);
    }

    // Project
    @GetMapping("/projects")
    public List<Map<String, Object>> projects() { return resourceService.listProjects(session()); }

    @PostMapping("/projects")
    public Map<String, Object> createProject(@RequestBody Map<String, Object> body) {
        return resourceService.createProject(session(), body);
    }

    // Runtime（仅展示已手动添加并成功注册的；轮询用于刷新在线状态）
    @GetMapping("/runtimes")
    public List<Map<String, Object>> runtimes() { return resourceService.listRuntimes(session()); }

    /** 手动添加：须已通过本机探测；写入当前会话工作区（daemonId 建议为 Desktop 实例 ID）。 */
    @PostMapping("/runtimes")
    public Map<String, Object> addRuntime(@RequestBody Map<String, Object> body) {
        return resourceService.addRuntimeForSession(
                session(),
                String.valueOf(body.get("provider")),
                String.valueOf(body.getOrDefault("hostName", "")),
                String.valueOf(body.getOrDefault("daemonId", ""))
        );
    }

    @DeleteMapping("/runtimes/{id}")
    public Map<String, Object> deleteRuntime(@PathVariable Long id) {
        resourceService.deleteRuntime(session(), id);
        return Map.of("ok", true);
    }

    @DeleteMapping("/runtimes/provider/{provider}")
    public Map<String, Object> deleteRuntimeByProvider(
            @PathVariable String provider,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String daemonId) {
        resourceService.deleteRuntimeByProvider(session(), provider, daemonId);
        return Map.of("ok", true);
    }

    // Chat
    @GetMapping("/chats")
    public List<Map<String, Object>> chats() { return orchestrationService.listSessions(session()); }

    @PostMapping("/chats")
    public Map<String, Object> createChat(@RequestBody Map<String, Object> body) {
        return orchestrationService.createSession(session(), body);
    }

    @GetMapping("/chats/{id}")
    public Map<String, Object> getChat(@PathVariable Long id) {
        return orchestrationService.getSession(session(), id);
    }

    @PostMapping("/chats/{id}/messages")
    public Map<String, Object> sendChat(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return orchestrationService.sendMessage(session(), id, String.valueOf(body.getOrDefault("content", "")));
    }

    // Issue
    @GetMapping("/issues")
    public List<Map<String, Object>> issues() { return orchestrationService.listIssues(session()); }

    @PostMapping("/issues")
    public Map<String, Object> createIssue(@RequestBody Map<String, Object> body) {
        return orchestrationService.createIssue(session(), body);
    }

    @GetMapping("/issues/{id}")
    public Map<String, Object> getIssue(@PathVariable Long id) {
        return orchestrationService.getIssue(session(), id);
    }

    @PutMapping("/issues/{id}")
    public Map<String, Object> updateIssue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return orchestrationService.updateIssue(session(), id, body);
    }

    @PostMapping("/issues/{id}/comments")
    public Map<String, Object> comment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return orchestrationService.addComment(session(), id, String.valueOf(body.getOrDefault("content", "")));
    }

    // Task
    @GetMapping("/tasks")
    public List<Map<String, Object>> tasks() { return orchestrationService.listTasks(session()); }

    @PostMapping("/tasks/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id) { return orchestrationService.cancelTask(session(), id); }

    @PostMapping("/tasks/{id}/rerun")
    public Map<String, Object> rerun(@PathVariable Long id) { return orchestrationService.rerunTask(session(), id); }

    // Inbox
    @GetMapping("/inbox")
    public Map<String, Object> inbox() {
        AuthPrincipal p = session();
        return Map.of("items", resourceService.listInbox(p), "unread", resourceService.unreadCount(p));
    }

    @PostMapping("/inbox/{id}/read")
    public Map<String, Object> readInbox(@PathVariable Long id) {
        resourceService.markRead(session(), id);
        return Map.of("ok", true);
    }

    // Daemon
    @PostMapping("/daemon/runtimes")
    public Map<String, Object> registerRuntime(@RequestBody Map<String, Object> body) {
        AuthPrincipal d = daemon();
        return resourceService.upsertRuntime(
                d.workspaceId(),
                String.valueOf(body.get("daemonId")),
                String.valueOf(body.get("provider")),
                String.valueOf(body.getOrDefault("hostName", "")),
                String.valueOf(body.getOrDefault("metaJson", "{}"))
        );
    }

    @DeleteMapping("/daemon/runtimes/provider/{provider}")
    public Map<String, Object> deleteDaemonRuntime(
            @PathVariable String provider,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String daemonId) {
        resourceService.deleteRuntimeByProvider(daemon(), provider, daemonId);
        return Map.of("ok", true);
    }

    @PostMapping("/daemon/heartbeat")
    public Map<String, Object> heartbeat(@RequestBody Map<String, Object> body) {
        daemon();
        resourceService.heartbeat(Long.parseLong(String.valueOf(body.get("runtimeId"))));
        return Map.of("ok", true);
    }

    @PostMapping("/daemon/claim")
    public Map<String, Object> claim(@RequestBody Map<String, Object> body) {
        return orchestrationService.claimTask(daemon(), Long.parseLong(String.valueOf(body.get("runtimeId"))));
    }

    @PostMapping("/daemon/tasks/{id}/report")
    public Map<String, Object> report(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        orchestrationService.reportTask(daemon(), id, body);
        return Map.of("ok", true);
    }
}
