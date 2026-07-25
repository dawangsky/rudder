package com.rudder.server.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 站内收件箱表 rb_inbox */
@TableName("rb_inbox")
public class InboxEntity {

    /** 通知id */
    @TableId("id")
    private Long id;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 接收用户id */
    @TableField("user_id")
    private Long userId;

    /** 通知标题 */
    @TableField("title")
    private String title;

    /** 通知正文 */
    @TableField("body")
    private String body;

    /** 关联类型：task / issue / chat 等 */
    @TableField("ref_type")
    private String refType;

    /** 关联对象id */
    @TableField("ref_id")
    private Long refId;

    /** 已读标记：0 未读 / 1 已读 */
    @TableField("read_flag")
    private Integer readFlag;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public Integer getReadFlag() { return readFlag; }
    public void setReadFlag(Integer readFlag) { this.readFlag = readFlag; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
