-- 项目：描述、状态、优先级、负责人、仓库、起止日期
ALTER TABLE rb_project
    ADD COLUMN description TEXT NULL COMMENT '项目描述' AFTER name,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'planned' COMMENT 'planned|in_progress|completed|canceled' AFTER description,
    ADD COLUMN priority VARCHAR(32) NOT NULL DEFAULT 'none' COMMENT 'none|low|medium|high|urgent' AFTER status,
    ADD COLUMN assignee_user_id BIGINT NULL COMMENT '负责人 user_id' AFTER priority,
    ADD COLUMN repo_url VARCHAR(512) NULL COMMENT '代码仓库 URL' AFTER local_path,
    ADD COLUMN start_date DATE NULL COMMENT '开始日期' AFTER repo_url,
    ADD COLUMN due_date DATE NULL COMMENT '截止日期' AFTER start_date,
    ADD KEY idx_project_assignee (assignee_user_id),
    ADD KEY idx_project_status (workspace_id, status);
