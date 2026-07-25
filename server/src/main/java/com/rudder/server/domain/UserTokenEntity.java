package com.rudder.server.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 用户 Token 表 rb_user_token（session / daemon / pat） */
@TableName("rb_user_token")
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getRevoked() { return revoked; }
    public void setRevoked(Integer revoked) { this.revoked = revoked; }
}
