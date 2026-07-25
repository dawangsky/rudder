## ADDED Requirements

### Requirement: 创建 Agent 须指定 Provider
用户创建 Agent 时系统 SHALL 要求选择 Provider（Cursor、Claude Code 或 Codex），并允许填写 Instructions 文本以限定领域或行为。

#### Scenario: 创建成功
- **WHEN** 用户提交名称、Provider 与可选 Instructions
- **THEN** 系统持久化该 Agent 并出现在 Agent 列表中

#### Scenario: 本机未安装对应 CLI
- **WHEN** 用户选择的 Provider 在本机 Daemon 探测结果中不存在
- **THEN** UI 或绑定 Runtime 流程 SHALL 提示安装该工具

### Requirement: 绑定 Runtime 与 Skills
Agent SHALL 可绑定在线 Runtime，并可挂载多个 Skill。

#### Scenario: 绑定后可派活
- **WHEN** Agent 已绑定可用 Runtime
- **THEN** 对该 Agent 的 Chat/指派/@ 触发可生成可被领取的 Task

### Requirement: Agent 状态
系统 SHALL 展示 Agent 状态（如 idle / working / offline / error），并反映 Runtime 在线与任务执行情况。

#### Scenario: Runtime 离线
- **WHEN** 绑定 Runtime 心跳超时或 Daemon 停止
- **THEN** Agent 显示为 offline 或不可派活提示
