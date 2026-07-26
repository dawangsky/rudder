-- 已有库升级：引导字段 + 多工作区支持（可重复执行需自行跳过已存在列）
-- mysql -u... rudder < server/src/main/resources/db/migration_20260726_onboarding_multiws.sql

ALTER TABLE rb_user
    ADD COLUMN onboard_role VARCHAR(64) NULL COMMENT '引导：角色' AFTER display_name,
    ADD COLUMN onboard_intent VARCHAR(64) NULL COMMENT '引导：使用目的' AFTER onboard_role,
    ADD COLUMN active_workspace_id BIGINT NULL COMMENT '当前选中的工作区' AFTER onboard_intent;

ALTER TABLE rb_workspace
    ADD COLUMN issue_prefix VARCHAR(16) NOT NULL DEFAULT 'WS' COMMENT 'issue 编号前缀' AFTER slug,
    ADD COLUMN created_by BIGINT NULL COMMENT '创建人 user_id' AFTER issue_prefix,
    ADD KEY idx_ws_created_by (created_by);

ALTER TABLE rb_workspace_member
    ADD COLUMN last_accessed_at DATETIME(3) NULL COMMENT '最近进入时间' AFTER role;
