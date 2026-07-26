-- Rudder 初始表结构（一账号多工作区）
-- 在 MySQL 中先创建库：CREATE DATABASE rudder DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS rb_user (
    id                  BIGINT PRIMARY KEY COMMENT '雪花/分配 ID',
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    display_name        VARCHAR(128) NOT NULL DEFAULT '',
    onboard_role        VARCHAR(64)  NULL COMMENT '引导：角色',
    onboard_intent      VARCHAR(64)  NULL COMMENT '引导：使用目的',
    active_workspace_id BIGINT       NULL COMMENT '当前选中的工作区（多工作区切换）',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT NOT NULL DEFAULT 0
) COMMENT='用户账号';

CREATE TABLE IF NOT EXISTS rb_workspace (
    id           BIGINT PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    slug         VARCHAR(128) NOT NULL,
    issue_prefix VARCHAR(16)  NOT NULL DEFAULT 'WS' COMMENT 'issue 编号前缀，如 WS-123',
    created_by   BIGINT       NULL COMMENT '创建人 user_id',
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted      TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_workspace_slug (slug),
    KEY idx_ws_created_by (created_by)
) COMMENT='工作区（多租户边界；一用户可加入多个）';

-- 成员角色：owner / admin / member；一用户多行 = 多工作区
CREATE TABLE IF NOT EXISTS rb_workspace_member (
    id               BIGINT PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    user_id          BIGINT NOT NULL,
    role             VARCHAR(32) NOT NULL DEFAULT 'owner',
    last_accessed_at DATETIME(3) NULL COMMENT '最近进入时间，用于排序默认工作区',
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_ws_user (workspace_id, user_id),
    KEY idx_member_user (user_id)
) COMMENT='工作区成员（支持一账号多工作区）';

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

CREATE TABLE IF NOT EXISTS rb_skill (
    id           BIGINT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(512) NULL COMMENT '简介（可从 frontmatter 解析）',
    content      MEDIUMTEXT NOT NULL,
    source_type  VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT 'manual|url|runtime',
    source_ref   VARCHAR(512) NULL COMMENT '来源 URL 或运行时路径',
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted      TINYINT NOT NULL DEFAULT 0,
    KEY idx_skill_ws (workspace_id)
) COMMENT='工作区 Skill（任意智能体可挂载）';

CREATE TABLE IF NOT EXISTS rb_agent_skill (
    id         BIGINT PRIMARY KEY,
    agent_id   BIGINT NOT NULL,
    skill_id   BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_skill (agent_id, skill_id)
) COMMENT='Agent-Skill 挂载';

CREATE TABLE IF NOT EXISTS rb_runtime_skill (
    id           BIGINT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    runtime_id   BIGINT NOT NULL,
    daemon_id    VARCHAR(128) NOT NULL DEFAULT '',
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(512) NULL,
    content      MEDIUMTEXT NOT NULL,
    source_path  VARCHAR(512) NOT NULL,
    content_hash VARCHAR(64) NOT NULL DEFAULT '',
    reported_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted      TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rt_skill_path (runtime_id, source_path),
    KEY idx_rt_skill_ws (workspace_id),
    KEY idx_rt_skill_runtime (runtime_id)
) COMMENT='Daemon 上报的本机 skill 缓存';
