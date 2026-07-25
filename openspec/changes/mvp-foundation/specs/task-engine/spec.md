## ADDED Requirements

### Requirement: Task 状态机
Task SHALL 具备状态 queued → dispatched → running → completed | failed | cancelled，并记录触发源（assign / mention / chat / rerun 等）。

#### Scenario: 正常完成
- **WHEN** Daemon 成功执行并上报完成
- **THEN** Task 变为 completed 且结果对 UI 可见

### Requirement: 取消与重跑
用户 SHALL 能取消未完成任务；失败任务 SHALL 支持一键重跑（新 Task 或等价 rerun 触发）。

#### Scenario: 取消 running
- **WHEN** 用户对 running Task 执行取消
- **THEN** 系统请求 Daemon 停止执行并将 Task 标为 cancelled

### Requirement: 结果回贴
任务完成或失败时，系统 SHALL 将摘要回写到对应 Issue 评论或 Chat 消息。

#### Scenario: Chat 回帖
- **WHEN** chat 触发的 Task 完成
- **THEN** 对话中出现 Agent 侧结果消息
