package com.rudder.server.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户 Token 表 rb_user_token（存哈希，不存明文）。 */
@Data
@TableName("rb_user_token")
public class UserTokenEntity {
    @TableId
    private Long id;
    private Long userId;
    private String tokenType;
    private String tokenHash;
    private String label;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Integer revoked;
}
