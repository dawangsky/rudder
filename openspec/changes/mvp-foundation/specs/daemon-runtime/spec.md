## ADDED Requirements

### Requirement: Daemon 启停与登录
Go CLI SHALL 提供 login 与 daemon start|stop|status，并支持配置任意 Self-Host Server 地址。

#### Scenario: 配置并启动
- **WHEN** 用户配置 server URL、login 成功后执行 daemon start
- **THEN** Daemon 保持常驻且不依赖 Desktop 窗口是否打开

### Requirement: CLI 探测与 Runtime 注册
Daemon 启动时 SHALL 探测本机已安装的 Cursor、Claude Code、Codex，并为当前工作区注册对应 Runtime；未安装的 SHALL 可被提示。

#### Scenario: 探测到工具
- **WHEN** PATH 上存在某 Provider 可执行文件
- **THEN** 对应 Runtime 在服务端显示在线（配合心跳）

### Requirement: 心跳与领任务
Daemon SHALL 定期心跳，并轮询领取属于其 Runtime 的 queued Task，准备环境后执行并流式/批量上报日志与状态。

#### Scenario: 领取执行
- **WHEN** 存在匹配 Runtime 的 queued Task
- **THEN** Daemon 在轮询周期内领取并转为 running（在线时目标 P95 触发到 running < 5s）

### Requirement: 关 UI 不停工
关闭 Desktop 窗口后，已 running 的任务 SHALL 继续执行直至结束（Daemon 仍在运行的前提下）。

#### Scenario: 关窗后完成
- **WHEN** 用户关闭 Desktop 但 Daemon 仍在运行
- **THEN** 任务可完成，再次打开 Desktop 可见最终结果
