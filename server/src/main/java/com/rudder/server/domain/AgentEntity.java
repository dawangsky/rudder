package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Agent 表 rb_agent */
@TableName("rb_agent")
@Data
@NoArgsConstructor
public class AgentEntity {

    /** Agent id */
    @TableId("id")
    private Long id;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** Agent 名称 */
    @TableField("name")
    private String name;

    /** 头像 URL 或标识 */
    @TableField("avatar")
    private String avatar;

    /** 简介描述 */
    @TableField("description")
    private String description;

    /** 领域 Instructions */
    @TableField("instructions")
    private String instructions;

    /** Provider：cursor / claude_code / codex / opencode / qwen / … */
    @TableField("provider")
    private String provider;

    /** 绑定的默认 Runtime id */
    @TableField("runtime_id")
    private Long runtimeId;

    /** 模型标识：default / sonnet / opus … */
    @TableField("model")
    private String model;

    /** 思考强度：cli | low | medium | high */
    @TableField("thinking_mode")
    private String thinkingMode;

    /** 最大并发数 */
    @TableField("max_concurrency")
    private Integer maxConcurrency;

    /** 状态 */
    @TableField("status")
    private String status;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0 正常 / 1 已删 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
