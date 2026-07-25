## Context

Rudder 从零开始建设。对标 Multica：Server 只做协调与状态，Daemon 在用户本机执行 Agent CLI。产品需求见 `docs/PRD-agent-orchestration-platform.md` v0.2 与本 change proposal。

## Goals / Non-Goals

**Goals:**

- 打通 MVP 验收：Agent 创建 → Daemon 在线 → Chat/指派/@ 派活 → 关窗不停 → Skill 注入 → 取消/重跑
- 仓结构支持二期独立 Web（`web/` 与 `desktop/` 分离）
- 表结构支持未来多人多工作区
- 工作目录：沙箱默认 + 项目路径优先
- UI：Desktop 中文浅色，左导航，默认 Chat，气质参考 Multica

**Non-Goals:**（详见 PRD §9.1.1，须保留在文档中）

- 独立浏览器 Web 产品、Docker Compose、多人邀请 UI、Windows、OAuth/SSO、Session 恢复、Autopilot、云 Runtime、ADE、Soul IM 整合等

## Decisions

### 1. 仓结构

```text
server/     Java Spring Boot 3
daemon/     Go daemon（可与 cli 同 module 多 command）
web/        Vue 3 + Vite 前端本体
desktop/    Electron 薄壳，加载 web 构建产物或 dev server
openspec/   规格
docs/       PRD 等
```

Desktop 通过窄接口 `HostBridge` 启停/监控本机 Daemon；业务页面不直接依赖 Electron。

### 2. Server 与实时

- JDK 21 + Spring Boot 3 + MyBatis-Plus + MySQL + Redis
- WebSocket：**Netty 4.x**（可与 Spring MVC 并存；推送任务/评论/Inbox 事件）
- 启动方式：`mvn spring-boot:run` 或 `java -jar`；**不做 Docker Compose**
- MySQL/Redis：仅通过本地配置/环境变量引用用户提供的连接信息，**不入库真实密码**

### 3. Daemon

- Go CLI：`login`、`daemon start|stop|status`、可配 `server_base_url`
- 启动时探测 PATH 上 Cursor / Claude Code / Codex；缺失则提示
- 按 workspace × provider 注册 Runtime；心跳（如 15s）；**轮询领任务**（Wakeup 二期）
- Provider 适配器接口统一 spawn / stream / cancel

### 4. 工作目录解析

```text
if project.local_path set for context:
  cwd = project.local_path  # serialize by realpath lock
  artifacts under sandbox env root (output/logs)
else:
  cwd = {RUDDER_WORKSPACES_ROOT|~/rudder_workspaces}/{workspace_id}/{task_id}/workdir/
```

Chat 与 Issue 共用该优先级。永不删除用户项目目录。

### 5. Agent 与 Chat

- 创建 Agent：必选 provider + 可选 instructions（领域限定）
- 新建 Chat：下拉已有 Agent；会话 UI 展示当前 Agent
- 每条用户消息 → Task(trigger=chat)

### 6. 鉴权

- 邮箱注册 + 登录（密码哈希）
- 用户会话 Token（Desktop/API）与 Daemon Token 分离

### 7. UI

- 左导航：Chat / Issues / Agents / Skills / Runtimes / Inbox / Settings
- 默认路由 Chat；Issue 列表+简单看板；中文；浅色；参考 Multica 密度与结构

## Risks / Trade-offs

| 风险 | 对策 |
|---|---|
| 三家 CLI 协议差异 | 适配器隔离；先打通一条端到端再补齐 |
| Netty WS 与 Spring 集成复杂度 | 明确端口/路径约定；单机 MVP 可同进程 |
| 项目路径并发写坏仓库 | 同路径串行锁 + waiting 状态 |
| 无 Docker 导致环境不一致 | 文档化 JDK/MySQL/Redis 版本；凭据由用户提供 |

## Migration Plan

- 全新库：Flyway/Liquibase 或 SQL 迁移脚本从空库初始化（实现时定）
- 无存量数据迁移

## Open Questions

- MySQL / Redis 具体连接信息：实现接入时向用户索取
- Project 页独立路由 vs 并入 Settings：实现 UI 时二选一，不影响模型
