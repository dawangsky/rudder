# Rudder Server

控制面：JDK 21 + Spring Boot 3。用 Java 命令启动，不做 Docker Compose（MVP）。

## Maven 多 settings（不影响其它项目）

本仓库在 `server/.mvn/` 下自带 settings，**不会改**你的 `~/.m2/settings.xml` 或 Maven 安装目录里的全局 settings。其它项目继续用原来的配置即可。

- 默认：`.mvn/settings/rudder-public.xml`（阿里云 Central）
- 切换 / 说明：见 [`.mvn/settings/README.md`](.mvn/settings/README.md)

在 `server/` 下直接执行 `mvn` 即会通过 `.mvn/maven.config` 自动选用上述文件。

## 启动

```bash
# 使用 JDK 21
export JAVA_HOME=/Users/wangda/opt/jdk21/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

cd server
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# 编辑 application-local.yml 填入 MySQL/Redis（勿提交）

mvn -DskipTests package
mvn spring-boot:run
# 或：java -jar target/rudder-server-*.jar
```

健康检查：`GET /api/health`
