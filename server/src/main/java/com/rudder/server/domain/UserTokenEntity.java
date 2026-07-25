package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户 Token 表 rb_user_token（session / daemon / pat） */
@TableName("rb_user_token")
@Data
@NoArgsConstructor
public class UserTokenEntity {

    /** Token 记录id */
    @TableId("id")
    private Long id;

    /** 用户id */
    @TableField("user_id")
    private Long userId;

    /** Token 类型：session | daemon | pat */
    @TableField("token_type")
    private String tokenType;

    /** Token 哈希（不明文存） */
    @TableField("token_hash")
    private String tokenHash;

    /** 备注标签 */
    @TableField("label")
    private String label;

    /** 过期时间 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 是否已吊销：0 否 / 1 是 */
    @TableField("revoked")
    private Integer revoked;
}
