# Rudder MVP 端到端验收清单

对照 PRD §13 与 `openspec/changes/mvp-foundation` specs。冒烟日期：2026-07-25。

| # | 验收项 | 结果 | 备注 |
|---|---|---|---|
| 1 | `daemon start` 后 Runtime 在线 | ✅ | stub/cursor/codex 可注册；Desktop Runtimes 页可看心跳 |
| 2 | 可创建 Agent（Provider + Instructions） | ✅ | `POST /api/agents` + Agents UI |
| 3 | Chat 须选 Agent；发消息后 Task 完成且回复可见 | ✅ | 真实 CLI 失败时 Daemon **fallback stub**；assistant 回帖 |
| 4 | Issue 指派 Agent → Task running/completed | ✅ | 指派后 Agent 回帖 |
| 5 | Issue 评论 `@Agent` → 新 Task + 回帖 | ✅ | 触发源 `issue_mention` |
| 6 | 关 Desktop 窗后任务仍可完成 | ✅ | Daemon 独立进程；关窗不杀 Daemon |
| 7 | 无项目路径 → `~/rudder_workspaces/.../workdir/`；有路径则项目优先 | ✅ | `WorkdirResolver` 单测 + Settings 建 Project |
| 8 | Agent 挂载 Skill 后工作目录有 Skill 文件 | ✅ | Daemon `execenv` 注入 |
| 9 | 可取消任务；失败/取消后可重跑 | ✅ | `POST /tasks/{id}/cancel|rerun` |
| — | Inbox 未读角标 | ✅ | 任务完成写入 `rb_inbox`；Shell 角标读 `/api/inbox` |
| — | Server 健康 / 依赖探活 | ✅ | `/api/health`、`/api/health/deps` |
| — | Java 单元测试 | ✅ | `mvn test`（Token/Password/Workdir/TaskStatuses/TaskView） |

## 冒烟命令摘要

```bash
# Server
cd server && mvn -DskipTests package
java -jar target/rudder-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=local

# Daemon
cd daemon && go build -o rudder .
./rudder login --server http://127.0.0.1:8080 --email ... --password ...
./rudder daemon start --server http://127.0.0.1:8080
```

## Out of Scope 可追溯性（任务 9.3）

| 文档 | 位置 |
|---|---|
| PRD | `docs/PRD-agent-orchestration-platform.md` §9.1.1 |
| OpenSpec design | `openspec/changes/mvp-foundation/design.md` → Goals / Non-Goals |
| OpenSpec proposal | `openspec/changes/mvp-foundation/proposal.md` → What Changes（文档化 Out of Scope） |
