# FabPilot 本地环境搭建

## 必需软件

1. Git（已安装）。
2. Eclipse Temurin JDK 21（必须；Spring Boot 3 不支持当前的 JDK 10）。
3. Docker Desktop（必须；用于启动 MySQL 8.4）。
4. IntelliJ IDEA Community 或 Ultimate（推荐）。

## 安装 JDK 21

下载安装 Eclipse Temurin 21 JDK x64：<https://adoptium.net/temurin/releases/?version=21>。

安装后关闭并重新打开 PowerShell，执行：

```powershell
java -version
mvn -version
```

两处都必须显示 Java 21。若 Maven 仍显示 Java 10，在 Windows 环境变量中将 `JAVA_HOME` 设置为 JDK 21 的安装目录，并将 `%JAVA_HOME%\bin` 放到 `Path` 的前面；随后重开终端和 IDE。

## 安装 Docker Desktop

从 <https://www.docker.com/products/docker-desktop/> 安装 Docker Desktop。首次启动时按提示启用 WSL 2；等待状态显示为 `Engine running`。

验证：

```powershell
docker --version
docker compose version
```

## 启动项目

在项目根目录执行：

```powershell
docker compose up -d
docker compose ps
```

看到 `fabpilot-mysql` 为 `healthy` 后，进入后端目录：

```powershell
cd mes-core
mvn test
mvn spring-boot:run
```

新开一个 PowerShell 窗口验证：

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

预期包含 `status = UP`、`service = mes-core`。

## MySQL 开发连接

- Host：`localhost`
- Port：`3306`
- Database：`fabpilot`
- Username：`fabpilot`
- Password：`fabpilot_dev_password`

仅用于本地开发。提交代码前不要将真实密码写入仓库。

## 当前已知阻塞

- 当前 Maven 使用 JDK 10，下载 Spring Boot 依赖时出现证书链错误；安装并切换到 JDK 21 后重新执行 `mvn test`。
- Docker Desktop 尚未检测到；安装并启动后再执行 `docker compose up -d`。
