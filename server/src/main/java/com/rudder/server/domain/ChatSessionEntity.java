package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Chat 会话表 rb_chat_session */
@TableName("rb_chat_session")
@Data
@NoArgsConstructor
public class ChatSessionEntity {

    /** 会话id */
    @TableId("id")
    private Long id;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 绑定 Agent id */
    @TableField("agent_id")
    private Long agentId;

    /** 关联项目id */
    @TableField("project_id")
    private Long projectId;

    /** 会话标题 */
    @TableField("title")
    private String title;

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
