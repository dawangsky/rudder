package com.rudder.server.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Issue 评论表 rb_issue_comment */
@TableName("rb_issue_comment")
public class IssueCommentEntity {

    /** 评论id */
    @TableId("id")
    private Long id;

    /** Issue id */
    @TableField("issue_id")
    private Long issueId;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 作者类型：user / agent */
    @TableField("author_type")
    private String authorType;

    /** 作者id（用户或 Agent） */
    @TableField("author_id")
    private Long authorId;

    /** 评论正文 */
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
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public String getAuthorType() { return authorType; }
    public void setAuthorType(String authorType) { this.authorType = authorType; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
