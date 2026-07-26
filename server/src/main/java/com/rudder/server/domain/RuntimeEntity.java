package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Daemon Runtime 表 rb_runtime */
@TableName("rb_runtime")
@Data
@NoArgsConstructor
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

    /** Provider：cursor / claude_code / codex / opencode / qwen / … */
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
}
