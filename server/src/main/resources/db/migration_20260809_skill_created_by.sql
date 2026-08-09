-- Skill：添加者 + 描述改为 TEXT（长 frontmatter 导入不再截断）
-- 可用 MySQL 客户端对 rudder 库执行（可重复执行）

-- 加长 description（无论旧长度多少）
ALTER TABLE rb_skill
    MODIFY COLUMN description TEXT NULL COMMENT '简介（可从 frontmatter 解析）';

-- 添加者列（已存在则忽略错误）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'rb_skill'
      AND COLUMN_NAME = 'created_by_user_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE rb_skill ADD COLUMN created_by_user_id BIGINT NULL COMMENT ''创建者用户 id'' AFTER source_ref',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'rb_skill'
      AND INDEX_NAME = 'idx_skill_creator'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE rb_skill ADD KEY idx_skill_creator (created_by_user_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
