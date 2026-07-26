package com.rudder.server.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 项目表 rb_project */
@TableName("rb_project")
@Data
@NoArgsConstructor
public class ProjectEntity {

    @TableId("id")
    private Long id;

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    /** planned | in_progress | completed | canceled */
    @TableField("status")
    private String status;

    /** none | low | medium | high | urgent */
    @TableField("priority")
    private String priority;

    @TableField("assignee_user_id")
    private Long assigneeUserId;

    /** 本机绝对路径（为空则走沙箱 workdir） */
    @TableField("local_path")
    private String localPath;

    @TableField("repo_url")
    private String repoUrl;

    @TableField("start_date")
    private LocalDate startDate;

    @TableField("due_date")
    private LocalDate dueDate;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
