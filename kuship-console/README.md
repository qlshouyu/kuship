# kuship-console

用 Java 21 / Spring Boot 4.0.6 重做 rainbond-console 服务端。复用其全部对外契约
（URL 前缀 `/console/*`、`GRJWT` 鉴权、`general_message` 响应信封），共享同一 MySQL `console` 库，
经 region-api 操作 Kubernetes。前端 rainbond-ui / kuship-ui **零源码改动**，仅把代理目标指向本服务（8000）。

> 本轮为 **P0：契约层 + 登录鉴权**（OpenSpec change `kuship-console-p0-contract-auth`）。
> 设计与可行性见 `../docs/kuship-console-架构设计.md`。

## 已实现（P0）

| 域 | 内容 |
|----|------|
| 响应信封 | `GeneralMessageResponseBodyAdvice` 自动包装 `{code,msg,msg_show,data:{bean,list}}`；Page→`data.total`；`@SkipResponseWrapper` 例外 |
| 异常映射 | `GlobalExceptionHandler` + `ServiceHandleException`，HTTP 状态码=业务 code，兜底 500 带 `trace_id` |
| JWT 认证 | `GRJWT`/`jwt`/`Bearer` 前缀；HS256 自实现（`Mac`），与 PyJWT/djangorestframework-jwt **双向互认**，不受 jjwt 最小密钥长度限制；密钥来自环境变量 `SECRET_KEY` |
| Redis 黑名单 | 登出写黑名单（键=sha256(token)，TTL 对齐 exp），每请求验签后查黑名单 |
| 多租户上下文 | `RequestContext`(@RequestScope) + `TenantContextInterceptor`，从 `{team_name}`/`{region_name}` 注入 |
| 登录 | `POST /console/users/login`（form `nick_name`+`password`，支持 phone/email/nick_name），`encrypt_passwd` 与既有库逐字节一致 |
| 数据层 | JPA 连接共享 `console` 库，`ddl-auto=validate`，`UserInfo`/`RegionConfig` 反向映射，无 DB 外键 |
| RegionClient | 单 region-api 传输骨架（地址/凭证解析、认证优先级、重试+超时梯度、连接池缓存），**无域方法** |
| 健康检查 | `GET /console/healthz` |

不在本轮：`/openapi/v1`、多 region/rke2、各业务域接口、RegionClient 双向 TLS 实接、各业务实体。

## 配置（环境变量）

| 变量 | 默认 | 说明 |
|------|------|------|
| `SECRET_KEY` | （local 下有占位） | **必须与 rainbond-console 同源**；非 local 为空则拒绝启动 |
| `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_DB`/`MYSQL_USER`/`MYSQL_PASSWORD` | `127.0.0.1`/`3306`/`console`/`root`/空 | 共享 console 库 |
| `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD` | `127.0.0.1`/`6379`/空 | 会话黑名单 |
| `REGION_SSL_VERIFY` | `false` | 下游 region-api TLS 校验 |

## 构建与运行

```bash
mvn test          # 单元测试（17 项）
mvn package       # 产出 target/kuship-console.jar
SECRET_KEY=... MYSQL_HOST=... REDIS_HOST=... java -jar target/kuship-console.jar
```

## 本地端到端联调

```bash
docker run -d --name kuship-mysql -e MYSQL_ROOT_PASSWORD=kuship123 -e MYSQL_DATABASE=console -p 3307:3306 mysql:8.0
docker run -d --name kuship-redis -p 6380:6379 redis:7-alpine
# 导入 console 库 schema（或直连既有库），插入用户：password = encrypt_passwd(email+明文)
MYSQL_PORT=3307 REDIS_PORT=6380 SECRET_KEY=<与7070同源> java -jar target/kuship-console.jar

curl -s localhost:8000/console/healthz
curl -s -X POST localhost:8000/console/users/login -d nick_name=admin -d password=admin1234
```

验收基线：rainbond-ui 把代理 `proxyTarget` 指向 8000 即可登录；响应信封/错误码/JWT 与 rainbond-console(7070) 一致，
两端同源 `SECRET_KEY` 时 token 双向互认。region-api 兼容基线钉死 Rainbond v6.9.0-release（`reference/rainbond@44c5c34d`）。
