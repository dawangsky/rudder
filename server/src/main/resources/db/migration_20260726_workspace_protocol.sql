-- 工作区运行时协议目录
CREATE TABLE IF NOT EXISTS rb_workspace_protocol (
    id            BIGINT PRIMARY KEY,
    workspace_id  BIGINT NOT NULL,
    code          VARCHAR(64) NOT NULL COMMENT '协议标识，如 opencode',
    label         VARCHAR(128) NOT NULL,
    short_label   VARCHAR(64) NOT NULL DEFAULT '',
    bins_json     VARCHAR(512) NOT NULL DEFAULT '[]' COMMENT '探测可执行名 JSON 数组',
    command_hint  VARCHAR(255) NOT NULL DEFAULT '',
    region        VARCHAR(16) NOT NULL DEFAULT 'intl' COMMENT 'intl|cn|test',
    enabled       TINYINT NOT NULL DEFAULT 1,
    builtin       TINYINT NOT NULL DEFAULT 0 COMMENT '1=内置不可删',
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_ws_protocol (workspace_id, code),
    KEY idx_ws_protocol_ws (workspace_id)
) COMMENT='工作区支持的运行时协议';
