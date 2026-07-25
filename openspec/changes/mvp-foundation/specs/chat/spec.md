## ADDED Requirements

### Requirement: 开聊前必须有 Agent
系统 SHALL 禁止在未选择已创建 Agent 的情况下创建 Chat 会话。

#### Scenario: 无 Agent 引导
- **WHEN** 工作区尚无 Agent 且用户点击新建聊天
- **THEN** 下拉不可用或为空，并引导用户前往 Agents 页创建

### Requirement: 新建聊天选择 Agent
用户点击「新建聊天」时，系统 SHALL 以下拉列表展示已创建 Agent，选中后创建会话。

#### Scenario: 选择后进入会话
- **WHEN** 用户从下拉选择某 Agent 确认新建
- **THEN** 创建绑定该 Agent 的会话并进入对话界面

### Requirement: 会话展示当前智能体
打开 Chat 会话时，界面 SHALL 展示该会话绑定的 Agent（名称/头像等）。

#### Scenario: 识别回复者
- **WHEN** 用户打开已有会话
- **THEN** 可明显看到当前对话绑定的智能体

### Requirement: 发消息触发 Task
每条用户消息 SHALL 创建 Task（trigger=chat），并通过 WebSocket 展示 Agent 回复或进度。

#### Scenario: 消息即派活
- **WHEN** 用户在会话中发送一条消息且 Agent Runtime 在线
- **THEN** Task 进入执行，回复在对话中可见

### Requirement: 会话列表
系统 SHALL 提供会话列表；列表项宜展示关联 Agent 以便区分。

#### Scenario: 列表可见
- **WHEN** 用户打开 Chat 页
- **THEN** 左侧显示已有会话且可识别各自绑定的 Agent
