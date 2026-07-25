package com.rudder.server.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Daemon Runtime 表 rb_runtime */
@TableName("rb_runtime")
public class RuntimeEntity {

    /** Runtime id */
    @TableId("id")
    private Long id;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** Daemon 实例标识 */
    @TableField("daemon_id")
    private String daemonId;

    /** Provider：cursor / claude_code / codex / stub */
    @TableField("provider")
    private String provider;

    /** 主机名 */
    @TableField("host_name")
    private String hostName;

    /** 在线状态：online / offline */
    @TableField("status")
    private String status;

    /** 最近心跳时间 */
    @TableField("last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    /** 扩展元数据 JSON */
    @TableField("meta_json")
    private String metaJson;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public String getDaemonId() { return daemonId; }
    public void setDaemonId(String daemonId) { this.daemonId = daemonId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public String getMetaJson() { return metaJson; }
    public void setMetaJson(String metaJson) { this.metaJson = metaJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
