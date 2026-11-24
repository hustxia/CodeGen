# ============================================================
# codegen-server 多阶段构建
#
# 阶段 1：maven:3.9-eclipse-temurin-21 编译打包（产出 fat jar）
# 阶段 2：eclipse-temurin:21-jre 运行（体积小、无编译工具链）
#
# 镜像源说明：
#   默认使用 Maven 中央仓库（https://repo.maven.apache.org/maven2），
#   国内网络慢或拉取失败时，取消下面 settings 阿里云镜像的注释即可。
#
# 构建（在仓库根目录执行）：
#   docker build -t codegen-server:latest .
#
# 健康检查端点：/actuator/health（pom 已引 spring-boot-starter-actuator，
# 且 AuthContextFilter 白名单放行该路径，无需 token）。
# ============================================================

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先只拷 pom.xml 下载依赖：源码没变时依赖层直接命中缓存，构建更快
COPY pom.xml .
# 默认走 Maven 中央仓库；国内环境取消下行注释改用阿里云镜像
# RUN mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml
RUN mvn -B dependency:go-offline

# 再拷源码做真正编译；-DskipTests 由 CI/验证阶段统一跑测试，这里只出包
COPY src ./src
RUN mvn -B package -DskipTests

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# pom.xml 的 <finalName> 固定为 codegen-server（版本号不进文件名）
COPY --from=builder /build/target/codegen-server.jar app.jar

EXPOSE 8120

# 进程级兜底 + HTTP 探针：actuator health 在鉴权白名单内
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD wget -qO- http://127.0.0.1:8120/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
