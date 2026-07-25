# Rudder

人在 Issue / Chat 里说话，Agent 像同事一样认领、执行、回贴。执行靠本机 Daemon，不依赖 UI 是否打开。

## 仓库结构

| 目录 | 说明 |
|---|---|
| `server/` | 控制面：JDK 21 + Spring Boot 3 + MyBatis-Plus + MySQL + Redis；WebSocket（Netty 4.x） |
| `daemon/` | Go CLI + Daemon（登录、启停、领任务、调本机 Agent CLI） |
| `web/` | Vue 3 前端本体（业务 UI） |
| `desktop/` | Electron 薄壳（加载 `web/`，可启停本机 Daemon） |
| `openspec/` | 规格驱动开发（OpenSpec） |
| `docs/` | 产品 PRD |

## 环境要求

- **JDK 21**（本机示例：`/Users/wangda/opt/jdk21/Contents/Home`）
- Maven 3.9+
- Go 1.22+
- Node.js 20+（建议 pnpm）
- MySQL、Redis（连接信息本地配置，**不要提交真实密码**）
- macOS（MVP）

## Self-Host：启动 Server（Java 命令）

```bash
# 使用 JDK 21
export JAVA_HOME=/Users/wangda/opt/jdk21/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

cd server

# 复制本地配置并填写 MySQL/Redis（见 application-local.yml.example；勿提交真实密码）
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

# 在 server/ 下执行 mvn 会自动使用本仓库 .mvn/settings（不影响其它项目的 settings）
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 或打包后运行
mvn -DskipTests package
java -jar target/rudder-server-*.jar --spring.profiles.active=local
```

默认 HTTP 端口：`8080`（可在配置中修改）。

Maven 多 settings 说明见 [`server/.mvn/settings/README.md`](server/.mvn/settings/README.md)。

## Web + Desktop

```bash
# 前端开发（推荐 npm；本机 pnpm 10 可能拦截 esbuild 构建脚本）
cd web && npm install --legacy-peer-deps && npm run dev

# Electron（另开终端，开发模式加载 Vite）
cd desktop && npm install --legacy-peer-deps && npm run dev
```

## Daemon / CLI（Go）

macOS 较新系统建议使用 Go 1.23+ toolchain（`go.mod` 已声明 `toolchain go1.23.8`）：

```bash
cd daemon
go build -o rudder ./cmd/rudder
./rudder --help
./rudder login --server http://127.0.0.1:8080
./rudder daemon start
```

## 规格与开发流程

本仓库使用 [OpenSpec](https://github.com/Fission-AI/OpenSpec)。当前变更：`openspec/changes/mvp-foundation/`。

实现前请阅读对应 specs；每完成一组改动请本地提交。
