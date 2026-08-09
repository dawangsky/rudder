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

    @TableId("id")
    private Long id;

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("content")
    private String content;

    /** 附属文件 JSON：[{path, content}, ...] */
    @TableField("files_json")
    private String filesJson;

    /** manual | url | runtime */
    @TableField("source_type")
    private String sourceType;

    @TableField("source_ref")
    private String sourceRef;

    @TableField("created_by_user_id")
    private Long createdByUserId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
