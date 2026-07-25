## ADDED Requirements

### Requirement: 邮箱注册
系统 SHALL 允许用户使用邮箱与密码注册账号。

#### Scenario: 注册成功
- **WHEN** 用户提交未占用的合法邮箱与符合策略的密码
- **THEN** 系统创建用户并允许其登录

#### Scenario: 邮箱已存在
- **WHEN** 用户使用已注册邮箱注册
- **THEN** 系统拒绝并提示邮箱不可用

### Requirement: 邮箱登录
系统 SHALL 支持邮箱与密码登录，并返回可供 Desktop/API 使用的用户会话凭证。

#### Scenario: 登录成功
- **WHEN** 用户提交正确的邮箱与密码
- **THEN** 系统签发用户会话 Token

### Requirement: 默认工作区
注册成功后系统 SHALL 为该用户创建并进入一个默认 Workspace（MVP 产品不提供多工作区切换 UI）。

#### Scenario: 首次进入
- **WHEN** 新用户完成注册并登录
- **THEN** 其处于唯一默认工作区内且可使用核心功能

### Requirement: 多租户表结构预留
数据模型 SHALL 以 workspace_id 隔离业务数据，并保留成员与角色字段，以便后续多人多工作区，即使 MVP UI 不暴露邀请流程。

#### Scenario: 数据归属
- **WHEN** 创建 Issue/Agent/Chat 等对象
- **THEN** 记录必须关联 workspace_id

### Requirement: Daemon Token
系统 SHALL 支持签发与用户会话分离的 Daemon Token，供本机 Daemon 鉴权领任务与上报。

#### Scenario: Daemon 登录
- **WHEN** 用户通过 CLI `login` 成功
- **THEN** 本机保存 Daemon Token 且不与浏览器会话 Cookie 混用
