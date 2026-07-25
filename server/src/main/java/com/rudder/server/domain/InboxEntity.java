package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 站内收件箱表 rb_inbox */
@TableName("rb_inbox")
@Data
@NoArgsConstructor
public class InboxEntity {

    /** 通知id */
    @TableId("id")
    private Long id;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 接收用户id */
    @TableField("user_id")
    private Long userId;

    /** 通知标题 */
    @TableField("title")
    private String title;

    /** 通知正文 */
    @TableField("body")
    private String body;

    /** 关联类型：task / issue / chat 等 */
    @TableField("ref_type")
    private String refType;

    /** 关联对象id */
    @TableField("ref_id")
    private Long refId;

    /** 已读标记：0 未读 / 1 已读 */
    @TableField("read_flag")
    private Integer readFlag;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
