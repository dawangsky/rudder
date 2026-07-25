package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Issue 评论表 rb_issue_comment */
@TableName("rb_issue_comment")
@Data
@NoArgsConstructor
public class IssueCommentEntity {

    /** 评论id */
    @TableId("id")
    private Long id;

    /** Issue id */
    @TableField("issue_id")
    private Long issueId;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 作者类型：user / agent */
    @TableField("author_type")
    private String authorType;

    /** 作者id（用户或 Agent） */
    @TableField("author_id")
    private Long authorId;

    /** 评论正文 */
    @TableField("content")
    private String content;

    /** 关联任务id */
    @TableField("task_id")
    private Long taskId;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
