## Why

kuship 项目当前后端仍依赖 `reference/rainbond-console`（Python 3.6 + Django 2.2）作为参考实现，需要替换为 Java 技术栈以满足团队栈一致性、长期可维护性、未来 GraalVM Native 部署等目标。整个迁移工作量大（约 600+ 路由、126 service、3850 行 Region API client），必须先把工程骨架与共享数据库的"双写并行"基线落定，后续 12 个迁移 change 才能逐模块、可灰度地推进。

## What Changes

- 在仓库根的 `kuship-console/` 目录新建 Maven 单模块工程：Spring Boot 4.0.6 + Java 21 + Spring Data JPA + Spring Security + Actuator
- 锁定核心依赖：`spring-boot-starter-web/data-jpa/security/actuator/validation`、`mysql-connector-j`、`flyway-core/mysql`、`jjwt`、`querydsl-jpa`、`mapstruct`、`io.kubernetes:client-java`（rke2 模块预置）、`httpclient5`（region client 预置）、`lombok`
- 与 rainbond-console **共享同一个 MySQL `console` 库**（开发环境 `127.0.0.1:3306`，`root/123456`）；JPA 强制 `hibernate.ddl-auto=validate`，schema 演进由 rainbond-console（Django）一方掌控
- Flyway 配置为 `baseline-on-migrate=true`、`baseline-version=0`、migration 目录留空，仅作为"占位 + 防御性配置"
- 装好 SecurityFilterChain 占位（先 permitAll），暴露 `/actuator/health|info|metrics`
- 不使用 `server.servlet.context-path`，每个 Controller 显式带 `/console` 等前缀；Spring MVC 配置允许 trailing slash
- 定义 `ApiResult` / `GeneralMessage` 工具类作为响应包装的占位骨架（**不挂全局 ControllerAdvice，留给下个 change**）
- 仅暴露唯一业务端点 `GET /console/healthz`，返回 rainbond-console 兼容格式：`{"code":200,"msg":"success","msg_show":"OK","data":{"bean":{},"list":[]}}`
- 提供 JVM 模式 `Dockerfile`（`eclipse-temurin:21-jre`，端口 8080）
- 提供 `kuship-console/README.md` 与 `kuship-console/CLAUDE.md`，记录包结构、约束、共享数据库模式
- 配置 `.gitignore`（含 `application-local.yaml`，避免凭据进 git）
- **明确不在本 change 内**：业务 Controller/Service/Repository/Entity、JWT 实现、Region API client、全局响应/异常 ControllerAdvice、租户上下文拦截、CORS、GraalVM Native、`/openapi/v1/*` 路由

## Capabilities

### New Capabilities

- `kuship-console-app`：Java/Spring Boot 后端控制台主应用的顶层能力。本 change 仅落地工程骨架与基础设施层（数据库连接、Security 框架、健康检查、响应格式约定）；后续 12 个 change 在此 capability 上增量交付业务模块（账户/团队/集群/应用/插件/市场等）。

### Modified Capabilities

无。本 change 不改动 `kuship-ui-app` 的任何 spec 行为。

## Impact

- **代码**：新增 `kuship-console/` 目录树，新增 Maven 工程文件、Java 包结构、配置文件、Dockerfile、文档
- **数据库**：通过 JDBC 连接现有 `console` 库进行只读校验；不写入业务数据、不变更 schema
- **API 契约**：仅新增 `GET /console/healthz`，不影响现有 rainbond-console 与 kuship-ui 之间的契约
- **部署**：新增独立可部署单元 `kuship-console`，允许与现有 rainbond-console 并行运行（按 path 灰度切流）
- **依赖**：引入完整 Java/Spring 生态依赖；不影响 `kuship-ui`、`reference/*`、`standalone/`、`docker/` 等已有目录
- **后续 change 的母体**：本 change 的 design.md 给出 13 阶段迁移路线图，后续 `migrate-console-response-contract`、`migrate-console-region-client`、`migrate-console-account-team` 等 change 都构建在此骨架之上
