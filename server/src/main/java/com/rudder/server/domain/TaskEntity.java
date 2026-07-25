package com.rudder.server.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 任务表 rb_task */
@TableName("rb_task")
public class TaskEntity {

    /** 任务id */
    @TableId("id")
    private Long id;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 执行 Agent id */
    @TableField("agent_id")
    private Long agentId;

    /** 领取的 Runtime id */
    @TableField("runtime_id")
    private Long runtimeId;

    /** 关联项目id */
    @TableField("project_id")
    private Long projectId;

    /** 触发源：chat / assign / mention / rerun */
    @TableField("trigger_source")
    private String triggerSource;

    /** 任务状态：queued / dispatched / running / completed / failed / cancelled */
    @TableField("status")
    private String status;

    /** 任务提示词 */
    @TableField("prompt")
    private String prompt;

    /** 结果摘要 */
    @TableField("result_summary")
    private String resultSummary;

    /** 失败错误信息 */
    @TableField("error_message")
    private String errorMessage;

    /** Agent 工作目录 */
    @TableField("work_dir")
    private String workDir;

    /** 关联 Issue id */
    @TableField("issue_id")
    private Long issueId;

    /** 关联 Chat 会话 id */
    @TableField("chat_session_id")
    private Long chatSessionId;

    /** 关联 Chat 消息 id */
    @TableField("chat_message_id")
    private Long chatMessageId;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 开始执行时间 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 结束时间 */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getRuntimeId() { return runtimeId; }
    public void setRuntimeId(Long runtimeId) { this.runtimeId = runtimeId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getWorkDir() { return workDir; }
    public void setWorkDir(String workDir) { this.workDir = workDir; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Long getChatSessionId() { return chatSessionId; }
    public void setChatSessionId(Long chatSessionId) { this.chatSessionId = chatSessionId; }
    public Long getChatMessageId() { return chatMessageId; }
    public void setChatMessageId(Long chatMessageId) { this.chatMessageId = chatMessageId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
