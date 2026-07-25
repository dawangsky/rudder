## ADDED Requirements

### Requirement: 左侧导航壳
Desktop 应用 SHALL 提供左侧常驻导航：Chat、Issues、Agents、Skills、Runtimes、Inbox、Settings，右侧为内容区。

#### Scenario: 导航切换
- **WHEN** 用户点击某导航项
- **THEN** 右侧切换到对应页面

### Requirement: 默认进入 Chat
用户登录成功后，应用 SHALL 默认进入 Chat 页。

#### Scenario: 登录落地
- **WHEN** 用户完成登录
- **THEN** 首屏为 Chat

### Requirement: 中文浅色与参考气质
MVP UI SHALL 使用中文文案与浅色主题，整体信息架构与密度参考 Multica 控制台，品牌名为 Rudder。

#### Scenario: 语言主题
- **WHEN** 用户使用 Desktop
- **THEN** 界面为中文浅色（第一期不提供深色切换）

### Requirement: 前端与薄壳分离
业务 UI SHALL 实现于 `web/`（Vue 3）；`desktop/` 仅 Electron 壳并加载 web，以便二期独立 Web 部署时复用。

#### Scenario: 目录职责
- **WHEN** 开发者查看仓库结构
- **THEN** 业务页面位于 `web/`，窗口与 Daemon 托管位于 `desktop/`

### Requirement: Desktop 内嵌 CLI 与 Daemon 托管（对齐 Multica）
Desktop SHALL 内嵌/调用同仓 `rudder` CLI，使用固定 profile `desktop`（目录 `~/.rudder/profiles/desktop/`），与终端默认 CLI profile 隔离。

#### Scenario: 登录联动
- **WHEN** 用户在 Desktop 完成邮箱登录或注册
- **THEN** HostBridge 将同一账号的 Daemon Token 写入 Desktop profile，并重启该 profile 的 Daemon

#### Scenario: 启动自动拉起
- **WHEN** Desktop 应用启动且 Desktop profile 已有有效凭证
- **THEN** 自动 start Desktop Daemon；无需用户先执行 `rudder daemon start`

#### Scenario: 不接管 CLI Daemon
- **WHEN** 用户已在终端以默认 profile 运行 Daemon
- **THEN** Desktop 仍只管理 `desktop` profile，不停止、不复用 CLI 的 pid/凭证；二者可同时在线并各自注册 Runtime

### Requirement: 运行时页手动添加
Runtimes 页 SHALL 支持手动添加/移除 Provider；添加前本机探测，失败展示错误；列表展示当前工作区已注册 Runtime 的在线状态并由轮询刷新。

#### Scenario: 添加成功
- **WHEN** 探测成功且会话写入/Daemon 注册成功
- **THEN** 列表出现该 Provider，随后由 Desktop Daemon 心跳维持 online

### Requirement: Multica 式侧栏与设置二级（MVP 可用项）
Desktop 侧栏 SHALL 按 Multica 信息架构分组（账号头、搜索/新建、主导航、工作区、配置）；设置页 SHALL 提供二级菜单，其中「一般」「Daemon」MUST 可用。

#### Scenario: 进入 Daemon 设置
- **WHEN** 用户打开「配置 → 设置」或 `/settings/daemon`
- **THEN** 展示 Daemon 启停偏好与运行诊断（状态/PID/实例 ID/Profile/Server 等）

### Requirement: 置灰入口不进入 MVP 实现
下列入口 MAY 在 MVP UI 中以置灰（`soon`）展示以对齐信息架构，但 MUST NOT 在第一期实现业务逻辑（详见 PRD §9.1.1 / §9.2）：侧栏「自动化」「小队」「用量」；设置「个人资料」「偏好设置」「通知」；设置工作区「通用」「成员」；全局搜索 ⌘K 的真实检索。

#### Scenario: 点击置灰项
- **WHEN** 用户点击上述置灰入口
- **THEN** 无路由跳转、无报错崩溃；可提示即将推出（或保持 disabled）
