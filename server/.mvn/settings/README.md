# Maven 多 settings（仅本仓库）

本目录用于 **Rudder server** 自己的 Maven 配置，与其它项目隔离。

## 原则

| 文件 | 作用范围 |
|---|---|
| `server/.mvn/maven.config` + `settings/*.xml` | **仅**在本仓库 `server/` 下执行 `mvn` 时生效 |
| `~/.m2/settings.xml` | 其它项目 / 默认用户配置，**本仓库不修改** |
| `$MAVEN_HOME/conf/settings.xml` | Maven 安装全局配置，**本仓库不修改** |

通过 `-gs`（全局 settings）和 `-s`（用户 settings）同时指向仓库内文件，避免再合并进公司私服等外部配置。

## 已有 settings

| 文件 | 用途 |
|---|---|
| `settings/rudder-public.xml` | 默认：阿里云 Central，适合本机公司 Nexus 不可达时 |
| `settings/company-nexus.xml.example` | 示例：复制后改成你们可达的 Nexus |

## 切换本仓库使用的 settings

编辑 [`../maven.config`](../maven.config)，把 `-gs` / `-s` 两行改成目标文件，例如：

```text
-gs
.mvn/settings/rudder-public.xml
-s
.mvn/settings/rudder-public.xml
```

或（公司私服可用时）：

```text
-gs
.mvn/settings/company-nexus.xml
-s
.mvn/settings/company-nexus.xml
```

也可单次命令覆盖（仍不影响其它项目）：

```bash
cd server
mvn -gs .mvn/settings/rudder-public.xml -s .mvn/settings/rudder-public.xml -DskipTests package
```

## 不要做的事

- 不要为了跑 Rudder 去改其它项目的 settings
- 不要把含账号密码的 settings 提交到 Git（如需私服账号，用本地未跟踪文件或环境变量）
