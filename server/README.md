# Rudder Server

控制面：JDK 21 + Spring Boot 3。用 Java 命令启动，不做 Docker Compose（MVP）。

```bash
# 使用 JDK 21（本仓库构建会自动用 server/.mvn 覆盖公司私服）
export JAVA_HOME=/Users/wangda/opt/jdk21/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

cd server
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# 编辑 application-local.yml 填入 MySQL/Redis

mvn -DskipTests package
mvn spring-boot:run
# 或：java -jar target/rudder-server-*.jar
```

> 说明：若本机 `/Users/wangda/opt/maven-*/conf/settings.xml` 指向不可达 Nexus，请使用仓库内 `.mvn/settings.xml`（已通过 `.mvn/maven.config` 默认启用）。
