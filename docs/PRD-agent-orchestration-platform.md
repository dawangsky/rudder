# PRD：Rudder — Agent 协作编排平台（Multica 类）

| 项 | 内容 |
|---|---|
| 文档版本 | v0.3.1 |
| 状态 | 需求已锁定（开放问题已关闭）；v0.3 Desktop/Daemon；v0.3.1 侧栏置灰项入二期 |
| 创建日期 | 2026-07-25 |
| 更新日期 | 2026-07-26 |
| 产品正式名 | **Rudder** |
| 对标产品 | Multica |
| 文档目标 | 明确架构组成、功能模块、MVP 范围与分期；可执行需求以 OpenSpec 为准 |
| 研发流程 | OpenSpec（proposal → specs → design → tasks → apply → archive） |

---

## 1. 背景与问题

### 1.1 背景

AI Coding Agent（Claude Code、Codex、Cursor Agent 等）能力快速提升，但使用方式仍偏「个人终端会话」：

- 每次复制粘贴长 prompt，缺少沉淀
- 必须盯着终端，关窗口即中断心智（甚至中断执行）
- 多人协作时，Agent 不在同一个任务看板里
- 无法像给同事派活一样：在 Issue / 聊天里说一句话就让 Agent 开工

### 1.2 要解决的问题

| 痛点 | 期望 |
|---|---|
| Agent 与团队工作流割裂 | Agent 成为看板上一等公民（可指派、可 @、可回贴） |
| 触发成本高 | Issue 分配 / 评论 @ / Chat 发言即可触发执行 |
| 关窗口就停 | 独立 Daemon 常驻执行，UI 只是控制台 |
| 经验不复用 | Skill 沉淀 SOP，挂到 Agent 上自动注入 |
| 密钥与代码出域风险 | 执行在用户本机，Server 只做协调与状态 |

### 1.3 产品一句话

> **人在 Issue / Chat 里说话，Agent 像同事一样认领、执行、回贴；执行不依赖窗口是否打开。**

---

## 2. 产品定位与目标用户

### 2.1 定位

- **是什么**：人 + AI 共用的任务协作与 Agent 编排平台（偏 Linear/Jira + 本地 Agent Runtime）
- **不是什么**：不是再造一套完整 IDE（对标 Orca ADE）；不做云端代跑用户代码（MVP）

### 2.2 目标用户

| 角色 | 诉求 |
|---|---|
| 个人开发者 / Indie | 用 Issue/Chat 驱动本机多个 Agent，少盯终端 |
| 小团队 Tech Lead | 任务可见、可指派、可审计，Agent 与人同一看板 |
| 平台/工具型创业者 | 需要可自托管的 Agent 控制面 |

### 2.3 成功指标（初期）

| 指标 | 目标（MVP 后 4～8 周） |
|---|---|
| 从「发言/@」到 Agent 开始执行 | P95 < 5s（本机 daemon 在线时） |
| 关 UI 后任务仍可跑完 | 100%（daemon 存活前提下） |
| 核心触发路径可用 | 分配 Issue、评论 @、Chat 三条路径打通 |
| 用户主观 | 「像给同事派活」评分 ≥ 4/5 |

---

## 3. 总体架构

### 3.1 架构原则

1. **控制面与执行面分离**：Server 编排，Daemon 执行
2. **Agent 不跑在 Server 上**：代码、密钥、CLI 会话留在本机
3. **说话即派活**：Issue / Comment / Chat 都是一等触发入口
4. **UI 可关，任务不停**：桌面/Web 仅为客户端
5. **Provider 可插拔**：统一 Backend 适配层对接多种 Agent CLI

### 3.2 逻辑架构（四层）

```text
┌─────────────────────────────────────────────────────────┐
│  Client 层                                               │
│  Web（Next.js / Vue） · Desktop（Electron 薄壳） · CLI    │
└───────────────────────────┬─────────────────────────────┘
                            │ HTTPS + WebSocket
┌───────────────────────────▼─────────────────────────────┐
│  Control Plane（Server）                                  │
│  工作区 · 成员权限 · Issue/Comment/Chat · Task Queue       │
│  Agent 定义 · Skill · Autopilot · Inbox · 实时推送         │
│  （不执行 LLM，不接触用户代码与 API Key）                    │
└───────────────────────────┬─────────────────────────────┘
                            │ 轮询 / Wakeup + 心跳
┌───────────────────────────▼─────────────────────────────┐
│  Execution Plane（本机 Daemon）                           │
│  Runtime 注册 · 领任务 · 工作目录 · Skill 注入              │
│  exec Agent CLI · 流式回流 · Session 恢复                  │
└───────────────────────────┬─────────────────────────────┘
                            │ 子进程
┌───────────────────────────▼─────────────────────────────┐
│  Agent CLI（Claude Code / Codex / Cursor Agent / …）      │
│  真正调模型、改代码、跑命令                                  │
└─────────────────────────────────────────────────────────┘
```

### 3.3 已定技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| Server | JDK 21 + Spring Boot 3 + MyBatis-Plus + MySQL + Redis | 控制面；Java 命令启动，第一期不做 Docker Compose |
| 实时 | WebSocket（**Netty 4.x**） | 评论流、任务进度、Inbox；Redis 可用于后续多节点 |
| Daemon / CLI | Go | 单二进制；login / daemon / 基础操作 |
| 前端本体 | Vue 3（目录 `web/`） | 业务 UI；可被 Electron 加载，便于二期独立 Web |
| Desktop | Electron 薄壳（目录 `desktop/`） | **第一期主客户端**；内嵌同一套 CLI，托管 **独立 profile 的 Daemon**，不承载 Agent 执行 |
| 平台 | 第一期 **macOS** | Windows 二期；Linux 正式支持列入后续 |

> 关键点：**Daemon 独立于窗口**（关窗不停工）；独立浏览器 Web **不做第一期产品形态**（代码仍放 `web/`）。

### 3.3.1 Desktop 与 Daemon（对齐 Multica）

控制面与执行面分离不变；客户端侧采用与 Multica 相同的「壳 + 本机执行器」关系：

| 原则 | 说明 |
|---|---|
| Desktop 内嵌 CLI | `desktop/` 通过 HostBridge 调用同仓 Go 二进制 `rudder`，不另实现执行逻辑 |
| 登录即联动 | Desktop 会话登录成功后，自动换取 Daemon Token 并写入 **Desktop profile** 凭证；用户无需再手敲 `rudder login` |
| 启动即拉起 Daemon | Desktop 启动（或登录成功）后自动 `daemon start`（Desktop profile）；侧栏仍可手动启停 |
| Profile 隔离 | **Desktop Daemon** 与 **终端 CLI Daemon** 使用不同本机目录（凭证 / pid / 已添加 Provider 列表分离），互不覆盖 |
| 同机可多 Daemon | 允许 Desktop 与 CLI 各跑一个（甚至更多 profile）；各自向 Server 注册 **不同 Runtime 行** |
| Runtime 语义 | `Runtime = 某个 Daemon 实例 × 某一 Provider`；同一「实例 + 工作区 + Provider」唯一，重启不重复造行 |
| 手动添加（Rudder 差异） | 本机已安装 CLI **不会自动出现在列表**；须在「运行时」页手动添加（探测失败则注册失败）；轮询只刷新已添加项在线状态 |

本机目录约定（实现真相源见 OpenSpec）：

```text
~/.rudder/                      # 默认 CLI profile（终端 rudder login / daemon）
  credentials.json
  daemon.pid
  enabled_providers.json
  instance.json                 # 稳定 daemon 实例 ID
~/.rudder/profiles/desktop/     # Desktop 专用 profile（Electron 始终 --profile desktop）
  credentials.json
  daemon.pid
  enabled_providers.json
  instance.json
```

Token：用户会话 Token（Desktop UI）与 Daemon Token 分离；Desktop 登录时同时完成两者落盘（会话在渲染进程，Daemon Token 在对应 profile）。

### 3.4 核心对象模型

| 对象 | 含义 |
|---|---|
| Workspace | 多租户边界：成员、Issue、Agent、Skill、Project 均归属工作区（**表结构按多人多工作区设计**；MVP 产品仅单人单工作区） |
| Member | 人类成员（角色：owner/admin/member；MVP 简化） |
| Project | 可选业务容器；可配置本机绝对路径作为 Agent 工作目录（**项目路径优先于默认沙箱**） |
| Agent | 带身份的 AI 工作者（Instructions、Provider、Runtime、Skills）；**开 Chat 前必须先创建** |
| Runtime | `Daemon 实例 × 某款 Agent CLI（Provider）`；同机 Desktop/CLI 各注册各的，互不合并 |
| Issue | 工作单元；可指派给人/Agent；可归属 Project |
| Comment | Issue 下讨论；`@Agent` 触发任务 |
| Chat Session | 选择已有 Agent 后创建；可归属 Project；每条用户消息可触发任务 |
| Task | 一次执行实例（queued → dispatched → running → completed/failed/cancelled） |
| Skill | 可复用 SOP/说明，任务启动前注入工作目录 |
| Autopilot | 定时/Webhook（二期） |
| Inbox | 个人通知中心（被指派、被 @、任务完成等） |

### 3.4.1 Agent 工作目录（对齐 Multica）

**优先级（高 → 低）**

1. Chat/Issue 归属 Project 且 Project 配置了本机路径 → 使用该路径  
2. 否则使用默认沙箱：`{RUDDER_WORKSPACES_ROOT|~/rudder_workspaces}/{workspace_id}/{task_id}/workdir/`

- 项目路径模式下：`output/` / `logs/` 仍落在沙箱 env 根；**永不删除用户项目目录**  
- 同一真实本地路径上的任务 **串行加锁**

### 3.5 任务生命周期

```text
触发（指派 / @ / Chat / Autopilot）
    → Server 创建 Task(queued)
    → Daemon 领取 (dispatched)
    → 准备目录 + 注入 Skill + 启动 CLI (running)
    → 流式上报日志/评论/状态
    → completed | failed（可重试）| cancelled
```

**同 (Agent, Issue/Chat) 会话恢复**：保存 `session_id` + `work_dir`，下一轮尽量续跑，避免每次从零开始。

---

## 4. 功能模块总览

```text
Rudder
├── 1. 账号与工作区（MVP 单人单工作区；表结构预留多人）
├── 2. Issue 协作（列表 + 简单看板）
├── 3. Agent 管理（Provider + Instructions）
├── 4. Project（本地路径 / 工作目录优先级）
├── 5. Runtime / Daemon
├── 6. 任务执行引擎
├── 7. Chat（须先选 Agent）
├── 8. Skill
├── 9. Autopilot（二期）
├── 10. Inbox 与通知
├── 11. CLI / Desktop 客户端
└── 12. 系统设置与安全
```

---

## 5. 功能模块详述

### 5.1 账号与工作区

**目标**：多工作区隔离，团队可协作。

| 功能 | 说明 | MVP |
|---|---|---|
| 注册 / 登录 | **邮箱注册 + 登录**；支持 PAT / Daemon Token | ✅ |
| 工作区 | 注册后自动创建/进入默认工作区（单人单工作区产品流程） | ✅ |
| 表结构预留 | 成员、多工作区、角色字段预留，便于二期邀请协作 | ✅ 结构 / 二期产品 |
| 角色权限 | owner / admin / member | 简化；二期精细化 |
| OAuth / SSO | | 二期及以后 |
| Workspace Context | 工作区级系统提示 | 二期 |
| 仓库白名单 | Agent 仅可访问配置的 Git 仓库 | 二期 |

### 5.2 Issue 协作（产品灵魂载体）

**目标**：人和 Agent 共用同一套任务对象。

| 功能 | 说明 | MVP |
|---|---|---|
| Issue CRUD | 标题、描述、状态、优先级 | ✅ |
| 指派 Assignee | 人 **或** Agent（多态） | ✅ |
| 列表 / 看板视图 | 按状态列；筛选指派人 | ✅ 至少列表+简单看板 |
| 评论 | Markdown；支持 `@Member` / `@Agent` | ✅ |
| **@Agent 触发** | 评论含 @Agent → 自动入队 Task | ✅ **P0** |
| **指派触发** | Assignee 改为 Agent → 自动入队 Task | ✅ **P0** |
| 活动时间线 | 状态变更、指派、评论、任务结果 | ✅ |
| 订阅 / 反应 / 标签 / 子任务 | 增强协作 | 二期 |
| 附件 | 图片/文件 | 二期 |

**交互亮点（必须做好）**

1. 在 Issue 下写：「@后端Agent 帮我看看这个报错」→ Agent 开始执行并回贴  
2. 把 Assignee 改成某 Agent → 无需再 @ 也会开工  
3. 执行过程中 UI 实时显示「运行中 / 日志摘要 / 完成」

### 5.3 Agent 管理

**目标**：Agent 是「配置好的同事」，不是裸模型。

| 功能 | 说明 | MVP |
|---|---|---|
| Agent CRUD | 名称、头像、描述、**Instructions（可限定领域）** | ✅ |
| 绑定 Provider | **Cursor / Claude Code / Codex**；本机探测，未装则提示 | ✅ |
| 绑定 Runtime | 选择在哪台在线机器跑 | ✅ |
| 挂载 Skills | 多选 Skill | ✅ |
| 并发上限 | 每 Agent 最大并行 Task | ✅ 简单配置 |
| 状态展示 | idle / working / offline / error | ✅ |
| MCP / 自定义 Env/Args | 高级能力 | 二期 |
| 可见性 private/workspace | | 二期 |

**约束**：用户必须先创建 Agent，才能新建 Chat；创建时指定 Provider 与可选 Instructions。

### 5.4 Runtime / Daemon

**目标**：本机常驻执行器，关 UI 不停工；Desktop/CLI 双通路对齐 Multica。

| 功能 | 说明 | MVP |
|---|---|---|
| Daemon 安装与登录 | CLI：`login` / `daemon start|stop|status`；支持 `--profile` | ✅ |
| Desktop 登录联动 | Desktop 登录自动写入 Desktop profile 的 Daemon Token 并重启该 Daemon | ✅ |
| Desktop 自动拉起 | 应用启动后自动 start Desktop profile Daemon（无凭证则登录后再起） | ✅ |
| Profile 隔离 | Desktop 与 CLI 凭证/pid/启用列表分离；同机可并存 | ✅ |
| 手动添加 Runtime | 探测本机是否安装 → 写入启用列表 → 注册；未安装则失败 | ✅ |
| Runtime 注册键 | `workspace × provider × daemon_instance_id`（双 Daemon 各一行） | ✅ |
| 心跳与在线状态 | 心跳约 15s；超时约 45s 展示 offline | ✅ |
| 任务轮询 + Wakeup | 轮询兜底 + WS 唤醒（可先做轮询） | ✅ 轮询；Wakeup 二期 |
| Runtime 管理页 | 在线/离线、心跳、主机、实例/profile、添加/移除 | ✅ |
| 桌面端托管 Daemon | HostBridge 启停/状态；内嵌 CLI | ✅ |

**非功能约束**

- Daemon 崩溃后可重启并回收/重试孤儿任务  
- 任务工作目录隔离  
- 禁止子进程覆盖 Daemon 鉴权环境变量  
- Desktop 关窗不默认杀掉其 Daemon（与 Multica「关 UI 不停工」一致）；退出登录/切换账号可停止 Desktop profile Daemon  

**与 Multica 的差异（刻意保留）**

- Multica：Daemon 启动时自动探测 PATH 全量注册 Runtime  
- Rudder：须用户手动添加后才注册；避免「装了就出现」造成误用与噪声列表  

### 5.5 任务执行引擎

**目标**：统一「一次执行」的队列与状态机。

| 功能 | 说明 | MVP |
|---|---|---|
| Task 状态机 | queued/dispatched/running/completed/failed/cancelled | ✅ |
| 触发源标记 | assign / mention / chat / autopilot / rerun | ✅ |
| 流式日志回流 | 文本/工具调用摘要写回 Issue 或 Chat | ✅ 至少文本+状态 |
| 结果回贴 | 成功摘要、失败原因评论 | ✅ |
| 手动取消 / 重跑 | | ✅ |
| Session 恢复 | 同上下文续跑 | 二期优先做 |
| Token/用量统计 | | 二期 |
| 自动重试策略 | 可重试错误自动 re-queue | 二期 |

### 5.6 Chat

**目标**：不建 Issue 也能对话驱动 Agent（默认首页入口）。

| 功能 | 说明 | MVP |
|---|---|---|
| 前置条件 | **必须先创建 Agent**；无 Agent 时引导至 Agents 页 | ✅ |
| 新建会话 | 「新建聊天」→ **下拉选择已创建 Agent** | ✅ |
| 会话内展示 | 对话界面展示当前绑定的智能体 | ✅ |
| 发消息即触发 Task | 每条用户消息入队 | ✅ **P0** |
| 流式回复展示 | WebSocket（Netty 4.x）推送 | ✅ |
| 关联 Project | 可选；有项目本地路径时工作目录走项目路径 | ✅ |
| 会话列表 / 归档 | | ✅ 列表；归档二期 |
| 从 Chat 一键生成 Issue | 把结论沉淀为正式任务 | 二期 |

**布局**：左侧会话列表 + 右侧对话区（顶栏展示当前 Agent）。

**与 Issue 评论的差异**

| | Chat | Issue 评论 |
|---|---|---|
| 可见性 | 偏个人与 Agent | 工作区协作可见 |
| 触发 | 每条消息 | 需 @ 或指派 |
| 用途 | 探索、草稿、轻量请求 | 正式工作推进 |
| 入口 | 默认登录后进入 | Issues 导航 |

### 5.7 Skill

**目标**：小而确定的方法说明书（非整本书知识库）。

| 功能 | 说明 | MVP |
|---|---|---|
| Skill CRUD | Markdown 主文档 + 可选附件文件 | ✅ |
| 挂载到 Agent | | ✅ |
| 任务前注入 | 按 Provider 约定目录写入 | ✅ |
| 从 URL 导入 | | 二期 |
| Skill 目录检索（大量 Skill） | 知识库式路由 + 加载执行 | 三期 |

### 5.8 Autopilot（二期）

| 功能 | 说明 |
|---|---|
| Cron 定时 | 如每日 triage |
| Webhook / 手动触发 | 外部系统对接 |
| 模式 | create_issue / run_only |
| 并发策略 | skip / queue / replace |
| 运行历史 | 可追溯 |

### 5.9 Inbox 与通知

| 功能 | 说明 | MVP |
|---|---|---|
| 被指派 / 被 @ / 任务完成通知 | | ✅ 站内 |
| 未读角标 | | ✅ |
| 桌面系统通知 | | 二期 |
| 邮件/IM 集成 | | 三期 |

### 5.10 客户端

| 客户端 | MVP | 说明 |
|---|---|---|
| Desktop（Electron + `web/` Vue3） | ✅ | **主交互面**；中文浅色；左侧导航；视觉参考 Multica |
| CLI（Go） | ✅ | 登录 + daemon + 基础操作；可配 Self-Host Server 地址 |
| 独立浏览器 Web | 二期 | 复用 `web/` 部署；第一期不上线 |

### 5.10.1 Desktop UI 壳（MVP）

- 左侧常驻导航：Issues / Chat / Agents / Skills / Runtimes / Inbox / Settings  
- 登录后默认进入 **Chat**  
- Issue：列表 + 简单看板  
- 语言中文；主题浅色  

### 5.11 安全与合规

| 项 | 要求 |
|---|---|
| 凭证边界 | API Key、Git 凭据仅存本机 / CLI 环境 |
| Server 最小权限 | 只存任务元数据、评论、日志摘要，不存完整代码库 |
| Token | 用户 PAT 与 Daemon Token 分离 |
| 审计 | 关键操作写入 activity（指派、触发、取消） |
| 取消与超时 | 支持取消；running 超时由 sweeper 回收 |

---

## 6. 核心用户故事（P0）

1. **作为**开发者，**我希望**把 Issue 指派给「后端 Agent」，**以便**我去干别的时它继续改代码并回贴进度。  
2. **作为**开发者，**我希望**在 Issue 评论里 `@测试Agent 补两个用例`，**以便**不改指派人也能追加一次执行。  
3. **作为**开发者，**我希望**在 Chat 里直接问 Agent「帮我列这个模块的风险点」，**以便**不必先建 Issue。  
4. **作为**开发者，**我希望**关掉浏览器后任务仍在跑，**以便**编排不被窗口生命周期绑定。  
5. **作为**团队成员，**我希望**在看板上看到 Agent 与人的任务状态，**以便**协作透明。  
6. **作为**维护者，**我希望**给 Agent 挂上「Code Review Skill」，**以便**每次执行都按固定流程输出。

---

## 7. 关键流程（产品流程）

### 7.1 首次开通（Onboarding）

```text
注册登录（邮箱）→ 进入默认 Workspace
→ 本机安装 CLI，配置 Self-Host Server 地址并 login
→ daemon start（探测 CLI，注册 Runtime）
→ 创建第一个 Agent（选 Provider + Instructions）并绑定 Runtime
→ 打开 Chat（默认首页）→ 新建聊天下拉选 Agent → 发消息看到执行与回复
→（可选）创建 Project 配本地路径 / 创建 Issue 指派验证
```

### 7.2 「说话即派活」主路径

```text
用户在 Issue 评论 @Agent / 或 Chat 发送
→ Server 鉴权并解析触发对象
→ 写入 Comment/Message + 创建 Task(queued)
→（可选）WS wakeup Daemon
→ Daemon 领取并执行
→ 流式事件 → Server → 各客户端
→ Agent 以 Comment/Message 形式回复结果
→ Inbox 通知相关人
```

---

## 8. 非功能需求

| 类别 | 要求 |
|---|---|
| 可用性 | Daemon 在线时，触发到 running P95 < 5s |
| 可靠性 | Daemon 重启后可恢复队列；失败可手动重跑 |
| 扩展性 | Provider 以适配器接入，新增 CLI 不改核心队列 |
| 可观测 | Task 全链路状态可查；Daemon 日志可 `logs -f` |
| 安全 | 最小权限 Token；工作目录隔离；敏感环境变量保护 |
| 兼容 | 第一期 macOS；Windows 二期；Linux 后续正式支持 |
| 部署 | Self-Host；Server 用 **Java 命令**启动；**第一期不做 Docker Compose** |

---

## 9. MVP 范围与分期

### 9.1 MVP（第 1 期）——「能说话、能派活、能关窗」

**Must**

- 邮箱注册登录 + 默认单工作区（表结构预留多人多工作区）  
- Project：可配本机路径；工作目录优先级（项目 > 沙箱）  
- Issue：创建、列表+简单看板、指派 Agent、评论 `@Agent`  
- Chat：须先有 Agent；新建下拉选 Agent；发消息触发；默认首页  
- Agent：创建时选 Provider（Cursor/Claude Code/Codex）+ Instructions；绑定 Runtime/Skills  
- Daemon：安装、探测、心跳、领任务、执行、回贴（轮询领任务）  
- Task 状态机 + WebSocket 实时推送（Netty 4.x）  
- Skill：创建 + 挂载 + 注入  
- Desktop（Electron）+ Go CLI；Server 地址可配  

### 9.1.1 Out of Scope 清单（第一期不做，必须文档化以免二期遗漏）

| 能力 | 建议分期 | 备注 |
|---|---|---|
| 独立浏览器 Web 部署 | P1 | 仓内 `web/` 二期托管 |
| Docker Compose / 容器化 Server | P1 | 现用 Java 命令 |
| 多人协作 UI、邀请成员、角色精细化 | P1 | 表结构已预留 |
| 多工作区切换（产品侧） | P1 | 同上 |
| Windows 客户端 | P2 | 第一期仅 macOS |
| Linux Daemon/CLI 正式支持 | P1 | |
| OAuth / SSO / 复杂权限 | P1–P2 | |
| Session 恢复 | P1 | |
| 任务自动重试、Token/用量 | P1 | |
| Autopilot | P1 | |
| Desktop 系统通知 | P1 | |
| Inbox 邮件/IM | P2 | 站内 Inbox MVP 基础做 |
| Daemon Wakeup（相对轮询） | P1 | UI 侧已有 WS |
| 仓库白名单 / Workspace Context | P1 | |
| Skill URL 导入、检索路由 | P1 / P2 | |
| 从 Chat 一键生成 Issue | P1 | |
| 云端 Runtime | P2–P3 | |
| **侧栏「自动化」**（Autopilot / 定时·Webhook 编排 UI） | P1 | MVP 侧栏置灰占位，对齐 Multica 信息架构 |
| **侧栏「小队」**（Team / 协作小队） | P1 | 置灰占位；依赖多人成员与邀请 |
| **侧栏「用量」**（Usage / Token·任务用量看板） | P1 | 置灰占位；与 Token/用量统计一并做 |
| **设置 · 个人资料 / 偏好设置 / 通知** | P1 | 设置二级菜单置灰占位；MVP 仅「一般」「Daemon」可用 |
| **设置 · 工作区通用 / 成员** | P1 | 工作区设置二级菜单置灰；与邀请成员、多工作区一并做 |
| **全局搜索（⌘K）落地** | P1 | MVP 仅有入口 UI，无跨实体检索 |
| 完整 IDE / ADE | 明确不做或独立产品 | |
| 与 Soul IM 整合 | 不纳入 | |
| 移动端 | 近几期不做 | |

### 9.2 第 2 期——「像团队一样用」

从 §9.1.1 的 P1 项中选取并立项，优先建议：

1. **补齐侧栏置灰能力**：自动化（Autopilot UI）、小队、用量看板  
2. **设置页完善**：个人资料、偏好、通知；工作区通用与成员管理  
3. 独立 Web 部署（复用 `web/`）  
4. 邀请成员 / 多工作区切换 / 角色精细化  
5. Session 恢复、Daemon Wakeup、桌面系统通知  
6. Chat 转 Issue、OAuth/SSO（视客户需要）  
7. 全局搜索（⌘K）真实检索  

> MVP Desktop 已按 Multica 信息架构**展示**上述置灰入口，避免二期改导航结构；实现时去掉 `soon` 并接通路由即可。

### 9.3 第 3 期——「规模化」

- 多节点 Server + Redis fanout  
- Skill 检索路由  
- 云 Runtime  
- 开放 API 与 Webhook  
- 审计合规增强  

---

## 10. 页面与信息架构（MVP · Desktop）

```text
/login
/{workspace}
  /chat                   # 默认首页；左会话列表 + 右对话；新建下拉选 Agent
  /chat/:id
  /issues                 # 列表 + 简单看板（侧栏「我的 issue」与「Issues」同入）
  /issues/:id
  /projects
  /agents
  /skills
  /runtimes
  /inbox                  # 左列表 + 右详情
  /settings               # → /settings/daemon
  /settings/general       # Server 地址
  /settings/daemon        # Daemon 启停偏好 + 诊断
  # —— 以下侧栏/设置入口 MVP 置灰，二期接通（见 §9.1.1 / §9.2）——
  # /automation          # 自动化
  # /team                 # 小队
  # /usage                # 用量
  # /settings/profile|preferences|notifications
  # /settings/workspace-*|members
```

侧栏结构（对齐 Multica，置灰项不跳转）：

```text
账号头（切换/退出）
搜索…（⌘K，MVP 仅入口） / 新建 issue
收件箱 · 聊天 · 我的 issue
工作区：Issues · 项目 · 自动化(灰) · 智能体 · 小队(灰) · 用量(灰)
配置：运行时 · Skills · 设置
```

---

## 11. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 各 CLI 协议差异大 | 适配成本高 | MVP 接 Cursor / Claude Code / Codex；抽象 Provider 接口 |
| 用户未开 Daemon | 「指派了没反应」 | Onboarding 强引导 + UI 明确 Offline |
| 日志/评论刷屏 | 体验差 | 摘要回贴 + 详细日志折叠 |
| 与 Orca 定位混淆 | 范围膨胀 | 明确不做 ADE |
| 安全误触 | 信任崩盘 | 工作目录隔离、路径校验、取消能力 |

---

## 12. 与对标（Multica）的关系

| 维度 | 策略 |
|---|---|
| 核心体验 | **对齐**：Issue/Chat 发言驱动 Agent + 独立 Daemon |
| Desktop↔Daemon | **对齐**：内嵌 CLI、登录联动凭证、启动自动拉起、profile 隔离、同机 Desktop/CLI Daemon 可并存 |
| Runtime 出现方式 | **差异**：Multica 启动自动探测全量注册；Rudder **手动添加**后才注册 |
| 差异化（建议） | 可结合你方场景：中文团队体验、特定行业 Skill 包、与现有 IM/内部系统打通等（后续单独立项） |
| 不做 | 第一期不追求 Provider 数量与 Autopilot 完整度超过对标 |

---

## 13. 验收标准（MVP）

1. Desktop 登录后本机 Desktop profile Daemon 自动在线；手动添加 Runtime 后列表显示 online。CLI 另起 Daemon 时可并存为另一行。  
2. 可创建 Agent（指定 Cursor/Claude Code/Codex + Instructions）。  
3. Chat 新建须下拉选择已有 Agent；会话内展示该 Agent；发消息后 Task 执行且回复经 WS 可见。  
4. 新建 Issue 并指派 Agent，出现 Task 并进入 running。  
5. Issue 评论 `@Agent`，产生新 Task，Agent 回帖可见。  
6. 关闭 Desktop 窗口后，已 running 的任务仍能完成，再次打开可见结果。  
7. 无项目路径时任务落在 `~/rudder_workspaces/.../workdir/`；有项目本地路径时在该路径执行。  
8. Agent 挂载 Skill 后，任务工作目录中存在对应 Skill 文件。  
9. 用户可取消任务；失败任务可一键重跑。  

---

## 14. 开放问题（已关闭）

| # | 问题 | 结论 |
|---|---|---|
| 1 | 产品名称 | **Rudder** |
| 2 | Server 技术栈 | JDK21 + Spring Boot 3 + MyBatis-Plus + MySQL + Redis；WS 用 Netty 4.x |
| 3 | Agent CLI | Cursor、Claude Code、Codex；本机探测，未装提示 |
| 4 | 部署 | Self-Host；Java 命令跑 Server；不做 Docker Compose（一期） |
| 5 | 客户端 | Desktop 进 MVP；独立 Web 二期；`web/`+`desktop/` 分目录 |
| 6 | Soul IM | 独立新产品仓库，不整合 |
| 7 | Daemon/CLI 语言 | Go |
| 8 | 前端 | Vue 3 |
| 9 | 登录 | 邮箱注册+登录 |
| 10 | 工作区 | 产品单人单工作区；表结构支持多人多工作区 |
| 11 | 工作目录 | 默认沙箱 + 项目路径（项目优先）；Chat 同规则 |
| 12 | Server 地址 | Desktop/Daemon 可配任意 Self-Host |
| 13 | 平台 | 一期 macOS；Windows 二期 |
| 14 | UI | 左导航；默认 Chat；中文浅色；参考 Multica |
| 15 | Desktop↔Daemon | **对齐 Multica**：内嵌 CLI、登录联动、启动自动拉起、profile 隔离、同机可多 Daemon；Runtime 手动添加（Rudder 差异） |

---

## 15. 附录：模块优先级矩阵

| 模块 | P0 MVP | P1 | P2 |
|---|---|---|---|
| 工作区/成员 | ✅ 单人默认区 + 表预留 | 邀请/切换/精细权限 | SSO |
| Issue + 指派触发 | ✅ | 看板增强 | 依赖/子任务 |
| 评论 @ 触发 | ✅ | 富文本/附件 | 反应 |
| Chat 触发 | ✅ 须先选 Agent | 转 Issue | 多模态 |
| Agent 配置 | ✅ Provider+Instructions | MCP/Env | 私有可见性 |
| Project + 工作目录 | ✅ 双模式 | 多资源类型 | |
| Daemon/Runtime | ✅ 轮询 | Wakeup | 云 Runtime |
| Task 引擎 | ✅ + Netty WS | Session 恢复 | 智能重试 |
| Skill | ✅ | 导入 | 检索路由 |
| Inbox | ✅ 基础站内双栏 | 桌面通知；设置·通知 | 外部 IM |
| Autopilot / 自动化侧栏 | 置灰占位 | ✅ 接通 | 模板市场 |
| 小队 / 用量侧栏 | 置灰占位 | ✅ 接通 | |
| 设置二级（资料/偏好/工作区成员） | 置灰 + 一般/Daemon | ✅ 接通 | |
| Desktop | ✅ Multica 侧栏 IA | 系统通知/深度集成；⌘K 检索 | |
| 独立 Web | | ✅ 复用 web/ | |
| ADE | | | 明确不做或独立产品 |

---

**文档结束。** 可执行规格见 `openspec/`；二期从 §9.1.1 Out of Scope 清单勾选立项。
