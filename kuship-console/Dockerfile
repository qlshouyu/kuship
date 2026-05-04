# syntax=docker/dockerfile:1.7

# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 先解析依赖以利用层缓存
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

# 编译打包
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests

# ---------- Runtime stage ----------
FROM registry.cn-hangzhou.aliyuncs.com/egojit/alpine-jre21:3.23.4
WORKDIR /app

COPY --from=build /workspace/target/kuship-console.jar /app/kuship-console.jar

EXPOSE 8080

# 运行时由调用方注入 DB_URL / DB_USERNAME / DB_PASSWORD 等环境变量
ENTRYPOINT ["java", "-jar", "/app/kuship-console.jar"]
