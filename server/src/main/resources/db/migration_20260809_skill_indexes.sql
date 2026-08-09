-- Skills 相关索引优化（对齐常见查询；可重复执行）
-- 主要覆盖：
--   rb_skill: workspace 列表按 updated_at、同名查找、按 source_type 筛选
--   rb_agent_skill: 按 skill_id 反查挂载（列表 enrichment / 删除）
--   rb_runtime_skill: workspace + runtime 下列出

-- ---------- rb_skill ----------
-- 列表：eq workspace_id (+ deleted) order by updated_at desc, id desc
SET @idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rb_skill' AND INDEX_NAME = 'idx_skill_ws_updated'
);
SET @sql := IF(@idx = 0,
    'ALTER TABLE rb_skill ADD KEY idx_skill_ws_updated (workspace_id, deleted, updated_at, id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 同名导入/改名查重：eq workspace_id (+ deleted) eq name
SET @idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rb_skill' AND INDEX_NAME = 'idx_skill_ws_name'
);
SET @sql := IF(@idx = 0,
    'ALTER TABLE rb_skill ADD KEY idx_skill_ws_name (workspace_id, deleted, name)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 按来源筛选：eq workspace_id (+ deleted) eq source_type order by updated_at
SET @idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rb_skill' AND INDEX_NAME = 'idx_skill_ws_source'
);
SET @sql := IF(@idx = 0,
    'ALTER TABLE rb_skill ADD KEY idx_skill_ws_source (workspace_id, deleted, source_type, updated_at)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 单列 workspace 已被复合索引覆盖，可去掉以减写入放大
SET @idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rb_skill' AND INDEX_NAME = 'idx_skill_ws'
);
SET @sql := IF(@idx > 0, 'ALTER TABLE rb_skill DROP INDEX idx_skill_ws', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- rb_agent_skill ----------
-- enrich / delete by skill_id（uk 仅覆盖 agent_id 前缀）
SET @idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rb_agent_skill' AND INDEX_NAME = 'idx_agent_skill_skill'
);
SET @sql := IF(@idx = 0,
    'ALTER TABLE rb_agent_skill ADD KEY idx_agent_skill_skill (skill_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- rb_runtime_skill ----------
-- listRuntimeSkills: eq workspace_id eq runtime_id order by name
SET @idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rb_runtime_skill' AND INDEX_NAME = 'idx_rt_skill_ws_rt'
);
SET @sql := IF(@idx = 0,
    'ALTER TABLE rb_runtime_skill ADD KEY idx_rt_skill_ws_rt (workspace_id, runtime_id, name)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 单列 workspace / runtime 已被 uk 或复合索引覆盖
SET @idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rb_runtime_skill' AND INDEX_NAME = 'idx_rt_skill_ws'
);
SET @sql := IF(@idx > 0, 'ALTER TABLE rb_runtime_skill DROP INDEX idx_rt_skill_ws', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rb_runtime_skill' AND INDEX_NAME = 'idx_rt_skill_runtime'
);
SET @sql := IF(@idx > 0, 'ALTER TABLE rb_runtime_skill DROP INDEX idx_rt_skill_runtime', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- runtime_skill.description 与工作区 skill 对齐为 TEXT（长描述；不建全文索引）
ALTER TABLE rb_runtime_skill
    MODIFY COLUMN description TEXT NULL COMMENT '简介（可从 frontmatter 解析）';
