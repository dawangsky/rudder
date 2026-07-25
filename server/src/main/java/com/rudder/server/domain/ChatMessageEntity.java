package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Chat 消息表 rb_chat_message */
@TableName("rb_chat_message")
@Data
@NoArgsConstructor
public class ChatMessageEntity {

    /** 消息id */
    @TableId("id")
    private Long id;

    /** 会话id */
    @TableField("session_id")
    private Long sessionId;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 角色：user / assistant / system */
    @TableField("role")
    private String role;

    /** 消息正文 */
    @TableField("content")
    private String content;

    /** 关联任务id */
    @TableField("task_id")
    private Long taskId;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
