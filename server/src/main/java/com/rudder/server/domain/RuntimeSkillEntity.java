package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Daemon 上报的本机 skill 缓存 rb_runtime_skill（整包替换，物理删除） */
@TableName("rb_runtime_skill")
@Data
@NoArgsConstructor
public class RuntimeSkillEntity {

    @TableId("id")
    private Long id;

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("runtime_id")
    private Long runtimeId;

    @TableField("daemon_id")
    private String daemonId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("content")
    private String content;

    @TableField("source_path")
    private String sourcePath;

    @TableField("content_hash")
    private String contentHash;

    @TableField("reported_at")
    private LocalDateTime reportedAt;

    @TableField("deleted")
    private Integer deleted;
}
