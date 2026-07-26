package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作区运行时协议表 rb_workspace_protocol */
@TableName("rb_workspace_protocol")
@Data
@NoArgsConstructor
public class WorkspaceProtocolEntity {

    @TableId("id")
    private Long id;

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("code")
    private String code;

    @TableField("label")
    private String label;

    @TableField("short_label")
    private String shortLabel;

    /** JSON 数组字符串，如 ["opencode","oc"] */
    @TableField("bins_json")
    private String binsJson;

    @TableField("command_hint")
    private String commandHint;

    /** intl | cn | test */
    @TableField("region")
    private String region;

    @TableField("enabled")
    private Integer enabled;

    @TableField("builtin")
    private Integer builtin;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
