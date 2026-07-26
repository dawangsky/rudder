package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户账号表 rb_user */
@TableName("rb_user")
@Data
@NoArgsConstructor
public class UserEntity {

    /** 用户id */
    @TableId("id")
    private Long id;

    /** 登录邮箱 */
    @TableField("email")
    private String email;

    /** 密码哈希 */
    @TableField("password_hash")
    private String passwordHash;

    /** 显示名称 */
    @TableField("display_name")
    private String displayName;

    /** 引导：角色（工程师/产品经理等） */
    @TableField("onboard_role")
    private String onboardRole;

    /** 引导：使用目的 */
    @TableField("onboard_intent")
    private String onboardIntent;

    /** 当前选中的工作区 id（一账号多工作区） */
    @TableField("active_workspace_id")
    private Long activeWorkspaceId;

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
