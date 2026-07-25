## ADDED Requirements

### Requirement: Skill CRUD
系统 SHALL 支持以 Markdown 为主的 Skill 创建、编辑与删除。

#### Scenario: 创建 Skill
- **WHEN** 用户提交 Skill 名称与正文
- **THEN** Skill 可被挂载到 Agent

### Requirement: 任务前注入
Task 启动前，Daemon SHALL 将 Agent 已挂载 Skill 按 Provider 约定写入工作目录（或约定 skills 目录）。

#### Scenario: 注入可见
- **WHEN** 挂载了 Skill 的 Agent 开始执行 Task
- **THEN** 工作目录中存在对应 Skill 文件内容
