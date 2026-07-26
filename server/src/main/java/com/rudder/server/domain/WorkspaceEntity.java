package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作区表 rb_workspace */
@TableName("rb_workspace")
@Data
@NoArgsConstructor
public class WorkspaceEntity {

    /** 工作区id */
    @TableId("id")
    private Long id;

    /** 工作区名称 */
    @TableField("name")
    private String name;

    /** 工作区唯一标识 slug */
    @TableField("slug")
    private String slug;

    /** issue 编号前缀，如 WS-123 */
    @TableField("issue_prefix")
    private String issuePrefix;

    /** 创建人 user_id */
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
