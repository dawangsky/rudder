## ADDED Requirements

### Requirement: 站内通知
系统 SHALL 在用户被指派、被 @、或相关 Task 完成/失败时写入站内 Inbox，并支持未读角标。

#### Scenario: 任务完成通知
- **WHEN** 用户触发的 Task 完成
- **THEN** Inbox 出现对应未读通知

### Requirement: 排除外部通道
MVP SHALL NOT 要求邮件或外部 IM 推送（列入后续分期）。

#### Scenario: 仅站内
- **WHEN** 产生通知事件
- **THEN** MVP 仅保证站内 Inbox 可达，不依赖邮件/IM 通道
