-- Skill：添加者 + 加长描述（导入长 frontmatter）
-- 可用 MySQL MCP / mysql 客户端对 rudder 库执行

ALTER TABLE rb_skill
    ADD COLUMN created_by_user_id BIGINT NULL COMMENT '创建者用户 id' AFTER source_ref,
    MODIFY COLUMN description VARCHAR(2048) NULL COMMENT '简介（可从 frontmatter 解析）';

ALTER TABLE rb_skill
    ADD KEY idx_skill_creator (created_by_user_id);
