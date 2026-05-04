# kuship-console

Java/Spring Boot 后端控制台，将逐步替代 `reference/rainbond-console`（Python/Django 参考实现）。
本仓库当前阶段仅落地工程骨架（OpenSpec change `init-kuship-console`）。

## 技术栈

| 维度 | 选型 |
|------|------|
| 语言/JDK | Java 21 |
| 框架 | Spring Boot 4.0.6 |
| 构建 | Maven |
| ORM | Spring Data JPA + Hibernate 6.x（+ QueryDSL，apt 已配） |
| Schema 演进 | Flyway（仅 baseline，业务 schema 由 rainbond-console 管控） |
| 安全 | Spring Security 6（本骨架阶段 permitAll，下个 change 接 JWT） |
| 监控 | Spring Boot Actuator（`/actuator/health|info|metrics`） |
| K8s 客户端 | `io.kubernetes:client-java`（仅 rke2 模块用，已置 optional 占位） |
| HTTP Client | Apache HttpComponents 5（Region API client 用，已置 optional 占位） |

## 与 rainbond-console 的关系

- **共享同一个 MySQL `console` 库**：开发环境 `127.0.0.1:3306`，用户 `root`，密码 `123456`
- **schema 只读**：JPA `ddl-auto=validate`；schema 演进由 rainbond-console（Django migrations）统一负责
- **URL 路径完全兼容**：`/console/*` 严格保留，路径变量名（如 `team_name`、`region_name`、`service_alias`）保持 snake_case
- **响应格式严格一致**：所有 controller 返回 `{code, msg, msg_show, data:{bean, list, ...}}` 结构

> 详细约束见 [`CLAUDE.md`](./CLAUDE.md) 与 OpenSpec change [`init-kuship-console`](../openspec/changes/init-kuship-console)。

## 前置环境

- Java 21（推荐 Eclipse Temurin / Liberica）
- Maven 3.9+
- 本地运行的 MySQL，包含 rainbond-console 的 `console` 库（开发凭据 `root/123456`）
- （可选）Docker，用于容器化构建

## 本地启动

```bash
# 1. 复制本地 profile 模板（首次）
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
# 编辑 application-local.yaml，填入你的 MySQL 凭据

# 2. 启动应用
mvn -pl kuship-console -am spring-boot:run -Dspring-boot.run.profiles=local
# 或在 kuship-console/ 目录下：
# mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 构建
~/.m2/settings.xml使用aliyun profile 启动阿里云的maven仓库
```bash
# 仓库根
mvn -pl kuship-console clean package
# 产物：kuship-console/target/kuship-console.jar

# 直接运行 jar（需提前注入环境变量）
java -DDB_URL='jdbc:mysql://127.0.0.1:3306/console?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true' \
     -DDB_USERNAME=root -DDB_PASSWORD=123456 \
     -jar kuship-console/target/kuship-console.jar
```

## 容器化

```bash
docker build -t kuship-console:dev kuship-console/
docker run --rm -p 8080:8080 \
  -e DB_URL='jdbc:mysql://host.docker.internal:3306/console?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true' \
  -e DB_USERNAME=root \
  -e DB_PASSWORD=123456 \
  kuship-console:dev
```

## 验证

```bash
# 业务健康端点（rainbond-console 兼容格式）
curl -s http://localhost:8080/console/healthz | jq .
# {"code":200,"msg":"success","msg_show":"OK","data":{"bean":{},"list":[]}}

# trailing slash 兼容
curl -s http://localhost:8080/console/healthz/ | jq .

# Actuator 探活
curl -s http://localhost:8080/actuator/health | jq .
# {"status":"UP"}
```

## 账户/团队 端到端验证（migrate-console-account-team 起可用）

`migrate-console-account-team` 已落地完整 RBAC：登录、注册、改密、用户/团队/角色/权限/企业管理共 ~30 endpoint。配置同源 `JWT_SECRET_KEY` 后，**rainbond-console 与 kuship-console 双向 token 互认**。

```bash
# 部署侧必须设置 JWT_SECRET_KEY，与 rainbond-console Django 进程同源
# rainbond docker 部署中可以从运行进程提取（动态生成）：
docker exec kuship-rainbond nsenter -t $(docker exec kuship-rainbond pgrep -f "gunicorn.*goodrain" | head -1) \
  -p -m sh -c 'cd /app/ui && /app/ui/py_venv/bin/python -c "from goodrain_web.settings import SECRET_KEY; print(SECRET_KEY)"'
# → e.g. 5633bdb5b864af1b67c58b7039e2b354

export JWT_SECRET_KEY=5633bdb5b864af1b67c58b7039e2b354

# 启动 kuship-console（默认端口 8080，可用 server.port 改）
DB_URL='jdbc:mysql://127.0.0.1:3306/console?useSSL=false&serverTimezone=UTC' \
DB_USERNAME=root DB_PASSWORD=123456 \
  mvn -pl kuship-console -am spring-boot:run -Dspring-boot.run.profiles=local

# 1. 公开端点（无需 JWT）
curl -s http://localhost:8080/console/healthz | jq .
curl -s http://localhost:8080/console/enterprise/info | jq .   # 平台默认 enterprise 脱敏信息
curl -s http://localhost:8080/console/perms | jq .              # 权限元数据

# 2. 注册 + 登录 + 拿 token
curl -s -X POST http://localhost:8080/console/users/register \
  -H 'Content-Type: application/json' \
  -d '{"nick_name":"alice","email":"alice@example.com","password":"alicepass1"}' | jq .

TOKEN=$(curl -s -X POST http://localhost:8080/console/users/login \
  -H 'Content-Type: application/json' \
  -d '{"nick_name":"alice","password":"alicepass1"}' | jq -r '.data.bean.token')

# 3. 调 /console/users/details
curl -s http://localhost:8080/console/users/details -H "Authorization: GRJWT $TOKEN" | jq .

# 4. 跨服务互认：rainbond docker 内签的 token 直接调 kuship 的端点（已验证可行）
RAINBOND_TOKEN=$(docker exec kuship-rainbond nsenter -t $(pgrep -f gunicorn|head -1) -p -m sh -c \
  'cd /app/ui && /app/ui/py_venv/bin/python -c "
import jwt, time
print(jwt.encode({\"user_id\":1,\"username\":\"admin\",\"email\":\"x@y\",
  \"orig_iat\":int(time.time()),\"exp\":int(time.time())+3600},
  \"5633bdb5b864af1b67c58b7039e2b354\", algorithm=\"HS256\"))
"')
curl -s http://localhost:8080/console/users/details -H "Authorization: GRJWT $RAINBOND_TOKEN" | jq .
```

## 集群管理 端到端验证（migrate-console-region-cluster 起可用）

`migrate-console-region-cluster` 已落地集群生命周期 + License + 团队-集群关联 + Registry 共 ~25 endpoint。

```bash
# 1. 列出 enterprise 内所有集群
curl -s http://localhost:8080/console/enterprise/<eid>/regions \
  -H "Authorization: GRJWT $TOKEN" | jq .

# 2. 添加集群（admin 权限；token 是 kubectl-format YAML）
curl -s -X POST http://localhost:8080/console/enterprise/<eid>/regions \
  -H "Authorization: GRJWT $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d @- <<'EOF'
{
  "region_name": "r1",
  "region_alias": "测试集群 1",
  "desc": "test",
  "region_type": ["public"],
  "token": "ca.pem: xxx\nclient.pem: xxx\nclient.key.pem: xxx\napiAddress: https://172.20.0.5:6443\nwebsocketAddress: wss://172.20.0.5:6060\ndefaultDomainSuffix: gr.local\ndefaultTCPHost: 172.20.0.5\n"
}
EOF

# 3. 团队开通集群
curl -s -X POST http://localhost:8080/console/teams/<team_name>/region \
  -H "Authorization: GRJWT $TOKEN" \
  -H 'Content-Type: application/json' -d '{"region_name":"r1"}'

# 4. 查询团队已开通集群
curl -s http://localhost:8080/console/teams/<team_name>/region/query \
  -H "Authorization: GRJWT $TOKEN" | jq .

# 5. 查集群 feature flag（转发给 region API）
curl -s http://localhost:8080/console/teams/<team_name>/regions/r1/features \
  -H "Authorization: GRJWT $TOKEN" | jq .
```

## 当前阶段不包含的能力

- OAuth 登录（独立 change `migrate-console-oauth`）
- 邮件/短信注册重置（`migrate-console-misc`）
- RKE2 一键部署集群（独立 change `migrate-console-rke2`，涉及 k8s 直连 + SSE 日志）
- 应用 / 插件 / 市场 等业务路径（按 13 阶段路线分别落地）
- Region API 业务调用：`TenantOperations` 5 method + `ClusterOperations` 8 method 已完整落地，其余 12 个资源域接口由各业务 change 填充
- Registry V2 镜像列表（仅 hub_type=docker 适配，详见后续 hardening change）
- CORS
- GraalVM Native 编译

完整迁移路线图见 [`init-kuship-console/design.md`](../openspec/changes/archive/2026-05-01-init-kuship-console/design.md) 的 13 阶段路线表。
