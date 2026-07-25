## ADDED Requirements

### Requirement: Daemon 启停与登录
Go CLI SHALL 提供 `login` 与 `daemon start|stop|status`，并支持配置任意 Self-Host Server 地址；SHALL 支持 `--profile`（或环境变量 `RUDDER_PROFILE`）以隔离本机数据目录。

#### Scenario: 配置并启动
- **WHEN** 用户配置 server URL、login 成功后执行 daemon start
- **THEN** Daemon 保持常驻且不依赖 Desktop 窗口是否打开

#### Scenario: Profile 目录隔离
- **WHEN** 使用 `--profile desktop` 与默认（CLI）profile 分别 login/start
- **THEN** 各自使用独立的 credentials / pid / enabled_providers / instance.json，互不覆盖

### Requirement: 稳定 Daemon 实例 ID
每个 profile SHALL 持久化稳定的 `daemon_instance_id`；重启 Daemon SHALL 复用同一 ID，不得每次启动生成新 ID 导致 Runtime 重复膨胀。

#### Scenario: 重启保持同一实例
- **WHEN** 同一 profile 的 Daemon 停止后再 start
- **THEN** 向 Server 注册时携带同一 daemonId

### Requirement: 手动添加与 Runtime 注册
用户 SHALL 手动添加运行时：本机探测 Provider，未安装则失败；成功后写入该 profile 的启用列表并由 Daemon 向 Server 注册。Daemon **SHALL NOT** 在启动时自动把 PATH 上所有 CLI 注册为 Runtime。

#### Scenario: 未安装则失败
- **WHEN** 用户添加未安装的 Provider
- **THEN** 注册失败并返回可读错误，Server 不出现对应 online Runtime（或保持未添加）

#### Scenario: 探测到并已添加
- **WHEN** 用户已添加某 Provider 且 PATH 可执行、Daemon 在线
- **THEN** 对应 Runtime 在服务端显示 online（配合心跳）

### Requirement: Runtime 唯一键
Server 注册 Runtime 时唯一键 SHALL 为 `workspace_id + provider + daemon_id`，使同机 Desktop Daemon 与 CLI Daemon 可为同一 Provider 各占一行。

#### Scenario: 双 Daemon 并存
- **WHEN** Desktop profile 与 CLI profile 均对同一 workspace 注册 `cursor`
- **THEN** 列表出现两条 Runtime，心跳与领任务互不影响

### Requirement: 心跳与领任务
Daemon SHALL 定期心跳，并轮询领取属于其 Runtime 的 queued Task，准备环境后执行并流式/批量上报日志与状态。

#### Scenario: 领取执行
- **WHEN** 存在匹配 Runtime 的 queued Task
- **THEN** Daemon 在轮询周期内领取并转为 running（在线时目标 P95 触发到 running < 5s）

### Requirement: 关 UI 不停工
关闭 Desktop 窗口后，已 running 的任务 SHALL 继续执行直至结束（Daemon 仍在运行的前提下）。退出登录或切换账号时可停止 Desktop profile Daemon。

#### Scenario: 关窗后完成
- **WHEN** 用户关闭 Desktop 窗口但 Desktop profile Daemon 仍在运行
- **THEN** 任务可完成，再次打开 Desktop 可见最终结果
