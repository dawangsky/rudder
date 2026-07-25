# PRD：Agent 协作编排平台（Multica 类）

| 项 | 内容 |
|---|---|
| 文档版本 | v0.1 |
| 状态 | 草案 |
| 创建日期 | 2026-07-25 |
| 产品暂定名 | **AgentBoard**（可改） |
| 对标产品 | Multica |
| 文档目标 | 明确架构组成、功能模块、MVP 范围与分期，作为研发与设计的统一输入 |

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

### 3.3 推荐技术栈（可调整）

| 层 | 推荐 | 说明 |
|---|---|---|
| Server | Go（或 Java，若团队更熟） | 任务队列、WS、权限；Go 更利于同仓维护 daemon |
| Daemon / CLI | Go | 单二进制、并发领任务、跨平台分发 |
| Web | Next.js 或 Vue 3 | 看板与实时协作 UI |
| Desktop | Electron 薄壳（首选）或 Tauri | 只负责窗口 + 拉起/监控 daemon，不承载执行 |
| DB | PostgreSQL | Issue、任务、成员、技能等 |
| 实时 | WebSocket（+ 可选 Redis 做多节点 fanout） | 评论流、任务进度、Inbox |

> 与对标一致的关键点：**Daemon 独立于窗口**；桌面用不用 Electron 是体验选择，不是架构核心。

### 3.4 核心对象模型

| 对象 | 含义 |
|---|---|
| Workspace | 多租户边界：成员、Issue、Agent、Skill 均归属工作区 |
| Member | 人类成员（角色：owner/admin/member） |
| Agent | 带身份的 AI 工作者（人设、Instructions、Provider、Runtime、Skills） |
| Runtime | `Daemon × 某款 Agent CLI`（一台机器可注册多个） |
| Issue | 工作单元；可指派给人/Agent |
| Comment | Issue 下讨论；`@Agent` 触发任务 |
| Chat Session | 不依附 Issue 的私聊；每条用户消息可触发任务 |
| Task | 一次执行实例（queued → dispatched → running → completed/failed/cancelled） |
| Skill | 可复用 SOP/说明，任务启动前注入工作目录 |
| Autopilot | 定时/Webhook 自动创建 Issue 或直接跑 Task |
| Inbox | 个人通知中心（被指派、被 @、任务完成等） |

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
AgentBoard
├── 1. 账号与工作区
├── 2. Issue 协作（看板核心）
├── 3. Agent 管理
├── 4. Runtime / Daemon
├── 5. 任务执行引擎
├── 6. Chat
├── 7. Skill
├── 8. Autopilot（二期）
├── 9. Inbox 与通知
├── 10. CLI / Desktop 客户端
└── 11. 系统设置与安全
```

---

## 5. 功能模块详述

### 5.1 账号与工作区

**目标**：多工作区隔离，团队可协作。

| 功能 | 说明 | MVP |
|---|---|---|
| 注册 / 登录 | 邮箱密码或 OAuth；支持 PAT / Daemon Token | ✅ |
| 工作区 CRUD | 创建、切换、邀请成员 | ✅ |
| 角色权限 | owner / admin / member | ✅ 简化版 |
| Workspace Context | 工作区级系统提示，注入所有 Agent | 二期 |
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
| Agent CRUD | 名称、头像、描述、Instructions | ✅ |
| 绑定 Provider | 先支持 1～2 个（建议 Claude Code + Codex 或 Cursor Agent） | ✅ |
| 绑定 Runtime | 选择在哪台在线机器跑 | ✅ |
| 挂载 Skills | 多选 Skill | ✅ |
| 并发上限 | 每 Agent 最大并行 Task | ✅ 简单配置 |
| 状态展示 | idle / working / offline / error | ✅ |
| MCP / 自定义 Env/Args | 高级能力 | 二期 |
| 可见性 private/workspace | | 二期 |

### 5.4 Runtime / Daemon

**目标**：本机常驻执行器，关 UI 不停工。

| 功能 | 说明 | MVP |
|---|---|---|
| Daemon 安装与登录 | CLI：`login` / `daemon start|stop|status` | ✅ |
| CLI 自动探测 | PATH 上发现已安装 Agent | ✅ |
| Runtime 注册 | 按 workspace × provider 注册 | ✅ |
| 心跳与在线状态 | 心跳周期可配（如 15s） | ✅ |
| 任务轮询 + Wakeup | 轮询兜底 + WS 唤醒（可先做轮询） | ✅ 轮询；Wakeup 二期 |
| Runtime 管理页 | 在线/离线、最近心跳、手动诊断 | ✅ |
| 桌面端托管 Daemon | Electron 可一键启停（可选） | 二期 |

**非功能约束**

- Daemon 崩溃后可重启并回收/重试孤儿任务  
- 任务工作目录隔离  
- 禁止子进程覆盖 Daemon 鉴权环境变量  

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

**目标**：不建 Issue 也能对话驱动 Agent（你最看重的入口之一）。

| 功能 | 说明 | MVP |
|---|---|---|
| 创建会话 | 选择 Agent 开聊 | ✅ |
| 发消息即触发 Task | 每条用户消息入队 | ✅ **P0** |
| 流式回复展示 | WS 推送 | ✅ |
| 会话列表 / 归档 | | ✅ 列表；归档二期 |
| 从 Chat 一键生成 Issue | 把结论沉淀为正式任务 | 二期 |

**与 Issue 评论的差异（产品需写清）**

| | Chat | Issue 评论 |
|---|---|---|
| 可见性 | 偏个人与 Agent | 工作区协作可见 |
| 触发 | 每条消息 | 需 @ 或指派 |
| 用途 | 探索、草稿、轻量请求 | 正式工作推进 |

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
| Web | ✅ | 主交互面 |
| CLI | ✅ | 登录 + daemon + 基础 issue/agent 操作 |
| Desktop | 二期 | Electron 薄壳；**不替代 daemon** |

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
注册登录 → 创建/加入 Workspace
→ 本机安装 CLI 并 login
→ daemon start（探测 CLI，注册 Runtime）
→ 创建第一个 Agent 并绑定 Runtime
→ 创建 Demo Issue 并指派 → 看到执行与回贴
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
| 兼容 | macOS / Linux 优先；Windows 二期 |
| 部署 | 支持 Cloud（官方托管）与 Self-Host（Docker Compose） |

---

## 9. MVP 范围与分期

### 9.1 MVP（第 1 期）——「能说话、能派活、能关窗」

**Must**

- Workspace + 登录  
- Issue：创建、指派 Agent、评论 `@Agent`  
- Chat：选 Agent 发消息触发  
- Agent：创建、绑定 1～2 个 Provider + Runtime  
- Daemon：安装、探测、心跳、领任务、执行、回贴  
- Task 状态机 + 基础实时刷新  
- Skill：创建 + 挂载 + 注入  
- Web 控制台 + CLI  

**Out of scope（MVP 不做）**

- 完整 IDE / 内嵌终端编辑器浏览器（Orca 能力）  
- Autopilot、Squad 多 Agent 编排  
- 云端 Runtime  
- 复杂权限与 SSO  
- 移动端  

### 9.2 第 2 期——「像团队一样用」

- Desktop 薄壳、系统通知  
- Session 恢复、自动重试、用量  
- Autopilot、Project、标签/子任务  
- Wakeup 通道、更多 Provider  
- Inbox 完善、从 Chat 转 Issue  

### 9.3 第 3 期——「规模化」

- 多节点 Server + Redis  
- Skill 检索路由（大量 Skill）  
- 云 Runtime / 远程机器  
- 开放 API 与 Webhook 生态  
- 审计合规增强  

---

## 10. 页面与信息架构（MVP）

```text
/login
/{workspace}
  /issues                 # 列表 + 看板
  /issues/:id             # 详情、评论、任务运行条
  /chat                   # 会话列表
  /chat/:id               # 对话
  /agents                 # Agent 列表与配置
  /skills                 # Skill 管理
  /runtimes               # Runtime / Daemon 状态
  /inbox                  # 通知
  /settings               # 工作区、成员、个人 Token
```

---

## 11. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 各 CLI 协议差异大 | 适配成本高 | MVP 只接 1～2 个；抽象 Backend 接口 |
| 用户未开 Daemon | 「指派了没反应」 | Onboarding 强引导 + UI 明确 Offline 提示 |
| 日志/评论刷屏 | 体验差 | 摘要回贴 + 详细日志折叠 |
| 与 Orca 定位混淆 | 范围膨胀 | PRD 明确不做 ADE；IDE 能力不做 MVP |
| 安全误触（危险命令） | 信任崩盘 | 工作目录隔离、仓库白名单（二期）、取消能力 |

---

## 12. 与对标（Multica）的关系

| 维度 | 策略 |
|---|---|
| 核心体验 | **对齐**：Issue/Chat 发言驱动 Agent + 独立 Daemon |
| 差异化（建议） | 可结合你方场景：中文团队体验、特定行业 Skill 包、与现有 IM/内部系统打通等（后续单独立项） |
| 不做 | 第一期不追求 Provider 数量与 Autopilot 完整度超过对标 |

---

## 13. 验收标准（MVP）

1. 本机 `daemon start` 后，Runtime 在 Web 显示在线。  
2. 新建 Issue 并指派 Agent，无需额外操作即可出现 Task 并进入 running。  
3. 在 Issue 评论 `@Agent` 一段话，产生新 Task，Agent 回帖可见。  
4. 在 Chat 发送一条消息，Agent 流式/分段回复可见。  
5. 关闭 Web/Desktop 后，已 running 的任务仍能完成，再次打开可见结果。  
6. Agent 挂载 Skill 后，任务工作目录中存在对应 Skill 文件。  
7. 用户可取消任务；失败任务可一键重跑。  

---

## 14. 开放问题（需产品确认）

1. 产品正式名称与品牌语气？  你帮我起个有寓意的英文名字
2. Server 首选  Java技术栈，使用jdk21+springboot3+mybatis-plus+mysql+redis(有新增的技术栈再加)
3. MVP 优先对接哪 1～2 个 Agent CLI？先支持Cursor、claude code、codex三种，可以通过检测扫描本机已安装的，没有则提示安装 
4. 先做 Cloud 托管，还是 Self-Host 优先？   先选择Self-Host 
5. Desktop 是否进入 MVP，还是纯 Web + CLI？desktop进入mvp  
6. 是否需要与现有「Soul」IM 项目整合，还是独立新产品仓库？  独立新产品仓库

---

## 15. 附录：模块优先级矩阵

| 模块 | P0 MVP | P1 | P2 |
|---|---|---|---|
| 工作区/成员 | ✅ | 精细权限 | SSO |
| Issue + 指派触发 | ✅ | 看板增强 | 依赖/子任务 |
| 评论 @ 触发 | ✅ | 富文本/附件 | 反应 |
| Chat 触发 | ✅ | 转 Issue | 多模态 |
| Agent 配置 | ✅ | MCP/Env | 私有可见性 |
| Daemon/Runtime | ✅ | Wakeup | 云 Runtime |
| Task 引擎 | ✅ | Session 恢复 | 智能重试 |
| Skill | ✅ | 导入 | 检索路由 |
| Inbox | ✅ 基础 | 桌面通知 | 外部 IM |
| Autopilot | | ✅ | 模板市场 |
| Desktop | | ✅ | 深度系统集成 |
| ADE 能力（编辑器/内嵌浏览器） | | | 明确不做或独立产品 |

---

**文档结束。** 确认第 14 节开放问题后，可进入技术设计（TDD/架构详设）与 Sprint 拆分。
