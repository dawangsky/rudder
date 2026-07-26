-- Skills：来源字段 + 运行时 skill 缓存
-- 可用 MySQL MCP / mysql 客户端对 rudder 库执行

ALTER TABLE rb_skill
    ADD COLUMN description VARCHAR(512) NULL COMMENT '简介（可从 frontmatter 解析）' AFTER name,
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT 'manual|url|runtime' AFTER content,
    ADD COLUMN source_ref VARCHAR(512) NULL COMMENT '来源 URL 或运行时路径' AFTER source_type;

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
