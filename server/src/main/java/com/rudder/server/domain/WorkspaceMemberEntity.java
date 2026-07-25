package com.rudder.server.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作区成员表 rb_workspace_member（预留多角色）。 */
@Data
@TableName("rb_workspace_member")
public class WorkspaceMemberEntity {
    @TableId
    private Long id;
    private Long workspaceId;
    private Long userId;
    /** owner / admin / member */
    private String role;
    private LocalDateTime createdAt;
}
