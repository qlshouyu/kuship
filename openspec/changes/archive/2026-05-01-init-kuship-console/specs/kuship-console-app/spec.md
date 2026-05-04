## ADDED Requirements

### Requirement: Maven 工程骨架

kuship-console 模块 SHALL 以 Maven 单模块工程的形式存在于 `kuship-console/` 目录，使用 Java 21 与 Spring Boot 4.0.6，并且能够通过 `mvn clean package` 在本机一次构建成功，产出可执行 jar。

#### Scenario: 本机干净构建

- **WHEN** 在仓库根执行 `mvn -pl kuship-console clean package`
- **THEN** 构建过程在本机环境无任何 ERROR 或可消除的 WARNING 而结束
- **AND** 在 `kuship-console/target/` 下产出一个可执行的 Spring Boot fat jar

#### Scenario: 工程包结构存在

- **WHEN** 检查 `kuship-console/src/main/java/cn/kuship/console/` 目录树
- **THEN** 必须存在以下顶层包：`config`、`common/response`、`common/exception`、`infrastructure/jpa`、`infrastructure/region`、`infrastructure/k8s`、`modules`、`healthz`
- **AND** Spring Boot 启动类 `KuShipConsoleApplication` 位于 `cn.kuship.console` 根包下

### Requirement: 与 rainbond-console 共享 MySQL `console` 数据库

kuship-console SHALL 通过 JPA 连接到 rainbond-console 已有的 MySQL `console` 数据库；启动时必须以 `hibernate.ddl-auto=validate` 的模式校验 schema 与 Hibernate 元数据一致；在任何环境下 SHALL NOT 通过 Hibernate 生成或修改任何表结构。

#### Scenario: 本地环境连接成功

- **WHEN** 本机已运行 rainbond-console 的 MySQL（`127.0.0.1:3306`，用户 `root`，密码 `123456`，库名 `console`）
- **AND** 以 `local` profile 启动 kuship-console
- **THEN** 应用成功启动，无 schema 校验失败异常

#### Scenario: 凭据不进 git

- **WHEN** 在仓库执行 `git status --ignored` 或检查 `.gitignore`
- **THEN** `kuship-console/src/main/resources/application-local.yaml` 处于被忽略状态
- **AND** 提交到仓库的 `application.yaml` 中数据库密码以环境变量占位（如 `${DB_PASSWORD:123456}`）形式存在，不直接写明文

#### Scenario: 禁止 schema 演进

- **WHEN** 检查 Spring 配置
- **THEN** `spring.jpa.hibernate.ddl-auto` 的值必须是 `validate`
- **AND** Flyway 配置 `baseline-on-migrate=true`、`baseline-version=0`
- **AND** `kuship-console/src/main/resources/db/migration/` 目录存在但不包含任何创建/修改业务表的 SQL 文件

### Requirement: `/console/healthz` 健康检查端点

kuship-console SHALL 暴露 `GET /console/healthz` 端点，返回 HTTP 200 与 rainbond-console 兼容的 `general_message` 响应包结构。

#### Scenario: 返回兼容响应格式

- **WHEN** 客户端发送 `GET /console/healthz`
- **THEN** 响应状态码为 200
- **AND** 响应 Content-Type 为 `application/json`
- **AND** 响应体严格符合形状 `{"code":200,"msg":"success","msg_show":"OK","data":{"bean":{},"list":[]}}`

#### Scenario: trailing slash 兼容

- **WHEN** 客户端发送 `GET /console/healthz/`（带尾部斜杠）
- **THEN** 响应状态码为 200，且响应体与不带尾斜杠的请求一致

### Requirement: 响应包装工具类

kuship-console SHALL 在 `cn.kuship.console.common.response` 包内提供 `ApiResult` POJO 与 `GeneralMessage` 静态工厂工具类，作为后续所有 controller 构造响应的唯一入口；这些工具类的输出 JSON 形状必须与 rainbond-console 的 `general_message(code, msg, msg_show, bean=, list=, **kwargs)` 完全一致。

#### Scenario: ApiResult 字段顺序与命名

- **WHEN** 序列化 `GeneralMessage.ok()` 返回的对象为 JSON
- **THEN** 顶层字段必须依次包含 `code`、`msg`、`msg_show`、`data`，且 `data` 是嵌套对象，至少包含 `bean` 和 `list` 两个键
- **AND** 字段名使用 snake_case（`msg_show` 而非 `msgShow`），以兼容 kuship-ui 的解析逻辑

#### Scenario: 支持任意 kwargs

- **WHEN** 调用 `GeneralMessage.ok(Map.of("total", 100, "page", 1))`
- **THEN** `data` 节点中除 `bean` 和 `list` 外，还包含 `total: 100` 与 `page: 1` 字段

### Requirement: Spring Security 占位

kuship-console SHALL 装配 `SecurityFilterChain`，本 change 范围内对所有路径放行（`permitAll`），但保留扩展点以便后续 change 接入 JWT 认证与 RBAC。

#### Scenario: 所有端点可未授权访问

- **WHEN** 客户端不带任何认证 header 发送 `GET /console/healthz` 或 `GET /actuator/health`
- **THEN** 响应状态码为 200，不返回 401 或 403

### Requirement: Actuator 健康/指标端点

kuship-console SHALL 暴露 Spring Boot Actuator 的 `health`、`info`、`metrics` 端点，挂在 `/actuator/*` 路径下，并且不与 `/console/*` 业务路径冲突。

#### Scenario: actuator/health 可访问

- **WHEN** 客户端发送 `GET /actuator/health`
- **THEN** 响应状态码为 200
- **AND** 响应体的 `status` 字段为 `UP`

### Requirement: URL 路径策略与契约

kuship-console SHALL NOT 配置 `server.servlet.context-path`；每个 controller 必须显式声明完整路径前缀（如 `/console`、未来的 `/openapi`、`/app-server` 等），且路径变量名必须与 rainbond-console 原始 Django URL 中保持一致（如 `team_name`、`region_name`、`service_alias`、`app_id` 等不得重命名为驼峰）。

#### Scenario: 不使用 context-path

- **WHEN** 检查 `application.yaml` 与 `application-local.yaml`
- **THEN** 不存在 `server.servlet.context-path` 配置项（或其值为空字符串）

#### Scenario: HealthzController 路径前缀显式声明

- **WHEN** 检查 `HealthzController` 类
- **THEN** 类或方法上存在 `@RequestMapping("/console/healthz")` 或等价显式注解，路径前缀不依赖全局 context-path

### Requirement: 容器化部署支持

kuship-console SHALL 提供 JVM 模式的 `Dockerfile`，基于 `eclipse-temurin:21-jre`，可被 `docker build` 构建并运行，容器暴露 8080 端口。

#### Scenario: 镜像构建成功

- **WHEN** 在 `kuship-console/` 目录执行 `docker build -t kuship-console:dev .`
- **THEN** 构建过程成功结束，产出可运行镜像

#### Scenario: 容器内 healthz 可访问

- **WHEN** 启动镜像并将 8080 映射到主机（容器需能访问宿主机 MySQL 或通过环境变量配置数据库地址）
- **THEN** `curl http://localhost:8080/console/healthz` 返回 200 与约定 JSON 格式

### Requirement: 模块文档

kuship-console SHALL 在模块根目录提供 `README.md`（面向开发者的本地启动与构建指南）和 `CLAUDE.md`（面向 AI 助手的包结构、约束、与 rainbond-console 共享数据库的关键说明），两份文档相互不重复但保持一致性。

#### Scenario: README 包含本地启动步骤

- **WHEN** 阅读 `kuship-console/README.md`
- **THEN** 文档至少包含：环境前置要求（Java 21、Maven、本地 MySQL）、`mvn clean package`、`mvn spring-boot:run -Dspring-boot.run.profiles=local`、Docker 构建命令、healthz 验证命令

#### Scenario: CLAUDE.md 记录关键约束

- **WHEN** 阅读 `kuship-console/CLAUDE.md`
- **THEN** 文档至少包含：包结构图、共享 rainbond-console 数据库的约束（schema 只读、不擅自加 `@Version`）、URL 路径策略（不用 context-path、保留 snake_case 路径变量）、响应格式契约（与 Django 版 `general_message` 完全一致）、技术栈版本（Spring Boot 4.0.6、Java 21、JPA + Hibernate + QueryDSL、Maven）、迁移路线图引用（指向本 change 的 design.md）
