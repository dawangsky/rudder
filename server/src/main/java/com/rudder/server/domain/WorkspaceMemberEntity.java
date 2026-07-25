package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作区成员表 rb_workspace_member */
@TableName("rb_workspace_member")
@Data
@NoArgsConstructor
public class WorkspaceMemberEntity {

    /** 成员记录id */
    @TableId("id")
    private Long id;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 用户id */
    @TableField("user_id")
    private Long userId;

    /** 角色：owner / admin / member */
    @TableField("role")
    private String role;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
