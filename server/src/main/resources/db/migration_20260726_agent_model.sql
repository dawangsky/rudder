-- 智能体：模型与思考强度
ALTER TABLE rb_agent
    ADD COLUMN model VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '模型标识' AFTER runtime_id,
    ADD COLUMN thinking_mode VARCHAR(32) NOT NULL DEFAULT 'cli' COMMENT 'cli|low|medium|high' AFTER model;
