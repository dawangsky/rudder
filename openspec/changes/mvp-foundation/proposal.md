## Why

个人与小团队使用 Cursor / Claude Code / Codex 时仍困在「盯终端、关窗即断、经验难沉淀」。Rudder 要把 Agent 变成可配置的同事：在 Chat / Issue 里说话即派活，本机 Daemon 常驻执行，控制面与执行面分离。需求已锁定，本 change 建立 MVP 可执行规格与落地设计，作为首期实现的唯一行为真相源。

## What Changes

- 新增 Self-Host 控制面（Java）与本机执行面（Go Daemon/CLI）
- 新增 Desktop（Electron + Vue3 `web/`）作为第一期主 UI；不上线独立浏览器版
- 邮箱注册登录；产品侧单人单工作区，库表按多人多工作区预留
- Agent：创建时指定 Provider + Instructions；开 Chat 前必须先有 Agent
- Chat / Issue 三条派活路径 + Task 状态机 + Netty WebSocket 实时
- 工作目录双模式：默认沙箱 + 项目本地路径（项目优先）
- Skill 挂载与注入；基础 Inbox
- 文档化第一期 Out of Scope，避免二期遗漏

## Capabilities

### New Capabilities

- `auth-workspace`：邮箱注册登录、默认工作区、Token（用户/Daemon）
- `agent`：Agent CRUD、Provider、Instructions、Runtime/Skills 绑定
- `project-workdir`：Project 与本地路径；工作目录解析优先级
- `issue`：Issue 列表/看板、指派触发、评论 @ 触发、活动流
- `chat`：须选 Agent 的会话、新建下拉、消息触发 Task、WS 展示
- `task-engine`：Task 状态机、取消/重跑、触发源、结果回贴
- `daemon-runtime`：探测 CLI、注册 Runtime、心跳、轮询领任务、执行回流
- `skill`：Skill CRUD、挂载、任务前注入
- `desktop-ui`：左导航壳、默认 Chat、中文浅色、参考 Multica
- `inbox`：站内基础通知与未读

### Modified Capabilities

- （无：仓库尚无主 specs，本 change 全部为新增）

## Impact

- 新建目录：`server/`、`daemon/`（或含 `cli/`）、`web/`、`desktop/`、`openspec/`
- 依赖：MySQL、Redis（连接信息向用户索取）；本机 Agent CLI
- 平台：MVP 仅保证 macOS
- 后续实现以本 change 的 specs/design/tasks 为准；PRD 为产品背景
