package com.rudder.server.domain;

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

    /** 项目id */
    @TableId("id")
    private Long id;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 项目名称 */
    @TableField("name")
    private String name;

    /** 本机绝对路径（为空则走沙箱 workdir） */
    @TableField("local_path")
    private String localPath;

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
