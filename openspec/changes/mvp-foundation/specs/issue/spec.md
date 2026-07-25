## ADDED Requirements

### Requirement: Issue CRUD 与视图
系统 SHALL 支持 Issue 的创建与编辑（标题、描述、状态、优先级），并提供列表视图与按状态分列的简单看板。

#### Scenario: 看板展示
- **WHEN** 用户打开 Issues 看板
- **THEN** Issue 按状态分列可见

### Requirement: 指派 Agent 触发任务
当 Assignee 被设置为某 Agent 时，系统 SHALL 自动创建 Task（trigger=assign）并进入队列。

#### Scenario: 指派即开工
- **WHEN** 用户将 Issue 指派给已绑定 Runtime 的 Agent
- **THEN** 产生 queued Task 且 Daemon 在线时可变为 running

### Requirement: 评论 @Agent 触发任务
当评论内容提及 @Agent 时，系统 SHALL 自动创建 Task（trigger=mention）。

#### Scenario: 评论派活
- **WHEN** 用户在 Issue 评论中 @ 某 Agent 并发送指令
- **THEN** 产生新 Task，执行结果以评论或活动形式回帖可见

### Requirement: 活动与运行状态
Issue 详情 SHALL 展示指派、评论、任务状态等活动，并显示运行中/完成/失败摘要。

#### Scenario: 详情可见运行态
- **WHEN** Issue 存在进行中或已完成的 Task
- **THEN** 详情页可看到对应状态摘要与相关活动
