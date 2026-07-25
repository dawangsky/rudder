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
