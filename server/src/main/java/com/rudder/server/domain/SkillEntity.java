package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Skill 表 rb_skill */
@TableName("rb_skill")
@Data
@NoArgsConstructor
public class SkillEntity {

    /** Skill id */
    @TableId("id")
    private Long id;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** Skill 名称 */
    @TableField("name")
    private String name;

    /** Skill 正文内容 */
    @TableField("content")
    private String content;

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
