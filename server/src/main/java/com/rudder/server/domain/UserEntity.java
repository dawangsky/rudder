package com.rudder.server.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户账号表 rb_user。 */
@Data
@TableName("rb_user")
public class UserEntity {
    @TableId
    private Long id;
    private String email;
    private String passwordHash;
    private String displayName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
