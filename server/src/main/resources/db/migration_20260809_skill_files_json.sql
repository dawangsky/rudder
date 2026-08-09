-- Skill 附属文件（JSON 数组：[{path, content}, ...]；主文件仍用 content = SKILL.md）
SET @col := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'rb_skill'
    AND COLUMN_NAME = 'files_json'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE rb_skill ADD COLUMN files_json MEDIUMTEXT NULL COMMENT ''附属文件 JSON [{path,content}]'' AFTER content',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
