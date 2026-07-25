## 1. 仓与工程脚手架

- [x] 1.1 创建 monorepo 目录：`server/`、`daemon/`、`web/`、`desktop/`，并补充根 README（含 Self-Host Java 启动说明）
- [x] 1.2 初始化 Java Spring Boot 3（JDK 21）工程与基础配置模板（MySQL/Redis 占位，**不写入真实密码**）
- [x] 1.3 初始化 Go module（CLI + daemon 命令骨架）
- [x] 1.4 初始化 Vue 3 + Vite（`web/`）与 Electron 薄壳（`desktop/`）可加载 web
- [x] 1.5 向用户索取并本地配置 MySQL/Redis 连接信息后验证连通

## 2. 认证与工作区

- [x] 2.1 实现邮箱注册/登录 API 与密码哈希
- [x] 2.2 用户会话 Token 与 Daemon Token 分离签发
- [x] 2.3 默认 Workspace 创建；表结构含 workspace_id/成员/角色预留
- [x] 2.4 Desktop 登录页对接认证

## 3. Desktop UI 壳

- [x] 3.1 左侧导航 + 路由（默认 Chat）；中文浅色布局参考 Multica
- [x] 3.2 HostBridge：启停/状态展示本机 Daemon（可先调 CLI）
- [x] 3.3 可配置 Server Base URL

## 4. Agent / Skill / Project

- [x] 4.1 Agent CRUD API + UI（Provider 三选一、Instructions）
- [x] 4.2 Skill CRUD + 挂载到 Agent
- [x] 4.3 Project 与本地路径配置 API + UI；路径校验
- [x] 4.4 Runtime 列表页展示在线/心跳

## 5. Daemon 与执行

- [x] 5.1 `login` / `daemon start|stop|status` + 心跳上报
- [x] 5.2 探测 Cursor / Claude Code / Codex 并注册 Runtime
- [x] 5.3 轮询领任务；沙箱 workdir 创建；Skill 注入
- [x] 5.4 项目本地路径模式 + 同路径串行锁
- [x] 5.5 Provider 适配器至少打通 1 个端到端，再补齐其余两个
- [x] 5.6 流式/批量上报日志与完成/失败

## 6. Task 引擎与实时

- [x] 6.1 Task 状态机与取消/重跑 API
- [x] 6.2 Netty 4.x WebSocket 推送任务与消息事件
- [x] 6.3 结果回帖到 Chat/Issue

## 7. Chat（P0）

- [x] 7.1 会话列表 + 新建下拉选 Agent + 会话内展示 Agent
- [x] 7.2 发消息创建 Task 并展示回复（WS）
- [x] 7.3 可选关联 Project 以走项目工作目录

## 8. Issue（P0）

- [x] 8.1 Issue CRUD；列表 + 简单看板
- [x] 8.2 指派 Agent 触发 Task
- [x] 8.3 评论 @Agent 触发 Task 与回帖
- [x] 8.4 详情活动/运行状态条

## 9. Inbox 与验收

- [x] 9.1 站内通知写入与未读角标
- [x] 9.2 按 PRD §13 / 本 change specs 做端到端验收清单勾选
- [x] 9.3 确认 Out of Scope 清单仍在 PRD 与本 change 中可追溯
