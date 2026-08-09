package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Issue 表 rb_issue */
@TableName("rb_issue")
@Data
@NoArgsConstructor
public class IssueEntity {

    /** Issue id */
    @TableId("id")
    private Long id;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 关联项目id */
    @TableField("project_id")
    private Long projectId;

    /** 标题 */
    @TableField("title")
    private String title;

    /** 描述 */
    @TableField("description")
    private String description;

    /** 状态：backlog / todo / in_progress / in_review / done（旧值 doing 读时归一为 in_progress） */
    @TableField("status")
    private String status;

    /** 优先级 */
    @TableField("priority")
    private String priority;

    /** 指派类型：agent / user */
    @TableField("assignee_type")
    private String assigneeType;

    /** 指派对象id */
    @TableField("assignee_id")
    private Long assigneeId;

    /** 创建人用户id */
    @TableField("created_by")
    private Long createdBy;

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
