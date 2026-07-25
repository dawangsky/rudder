package com.rudder.server.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Chat 消息表 rb_chat_message */
@TableName("rb_chat_message")
public class ChatMessageEntity {

    /** 消息id */
    @TableId("id")
    private Long id;

    /** 会话id */
    @TableField("session_id")
    private Long sessionId;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 角色：user / assistant / system */
    @TableField("role")
    private String role;

    /** 消息正文 */
    @TableField("content")
    private String content;

    /** 关联任务id */
    @TableField("task_id")
    private Long taskId;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
