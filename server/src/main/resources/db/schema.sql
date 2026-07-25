-- Rudder 初始表结构（多人多工作区预留；MVP 产品仅单人单工作区）
-- 在 MySQL 中先创建库：CREATE DATABASE rudder DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS rb_user (
    id            BIGINT PRIMARY KEY COMMENT '雪花/分配 ID',
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128) NOT NULL DEFAULT '',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted       TINYINT NOT NULL DEFAULT 0
) COMMENT='用户账号';

CREATE TABLE IF NOT EXISTS rb_workspace (
    id         BIGINT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    slug       VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted    TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_workspace_slug (slug)
) COMMENT='工作区（多租户边界）';

-- 成员角色预留：owner / admin / member
CREATE TABLE IF NOT EXISTS rb_workspace_member (
    id           BIGINT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    role         VARCHAR(32) NOT NULL DEFAULT 'owner',
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_ws_user (workspace_id, user_id),
    KEY idx_member_user (user_id)
) COMMENT='工作区成员（MVP 每用户一条 owner）';

CREATE TABLE IF NOT EXISTS rb_user_token (
    id         BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    token_type VARCHAR(32) NOT NULL COMMENT 'session | daemon | pat',
    token_hash VARCHAR(128) NOT NULL,
    label      VARCHAR(128) NOT NULL DEFAULT '',
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    revoked    TINYINT NOT NULL DEFAULT 0,
    KEY idx_token_hash (token_hash),
    KEY idx_token_user (user_id)
) COMMENT='用户会话 Token 与 Daemon Token（分离）';
