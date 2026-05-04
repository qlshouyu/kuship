## Context

kuship 仓库现有 4 个主目录：`reference/rainbond-console`（Python/Django 参考实现，只读 submodule）、`reference/rainbond-ui`、`reference/rainbond-chart`、`kuship-ui`（基于 rainbond-ui 改名的前端）。后端 `kuship-console` 目录尚未存在。

参考实现规模：
- 77 个 view 文件、约 520 个 `/console/*` 路由、52 个 `/openapi/v1/*` 路由
- 79 个 service、57 个 repository、`models/main.py` 1346 行
- `www/apiclient/regionapi.py` 3850 行（单文件 HTTP 客户端，对接 Rainbond Go 集群）
- 直接 K8s 客户端调用集中在 `console/utils/k8s_cli.py` 与 `console/views/rke2.py`，其余均通过 Region API

约束：
- kuship-ui 已经直接复用 rainbond-ui 代码，调用 `baseURL=/console/*`，**不会**为 Java 后端做任何改造
- rainbond-console 与 Rainbond Go 集群共享同一份 MySQL 数据库；kuship-console 也必须共享
- 团队栈是 Java，需要 Spring Boot 4.0.6 + Java 21 + Maven
- 长期目标支持 GraalVM Native 编译（V2，本 change 不交付）

## Goals / Non-Goals

**Goals：**

- 落地 `kuship-console/` Maven 工程骨架，可 `mvn clean package` 构建出可运行 jar
- 与 rainbond-console 共享 MySQL `console` 库；启动时 JPA `validate` 通过，证明命名策略与 schema 对齐
- 暴露 `GET /console/healthz`，响应严格符合 rainbond-console 的 `general_message` 格式
- 装好 Spring Security、Actuator、Flyway baseline、Dockerfile JVM 镜像
- 给出 13 阶段迁移路线图，让后续 12 个 change 有清晰的依赖与切片
- 沉淀 `kuship-console/CLAUDE.md`，记录包结构、共享数据库约束、响应格式契约

**Non-Goals：**

- 不实现任何业务接口（账户/团队/应用/集群/插件/市场等都不在本 change）
- 不实现 JWT 认证（Security 暂时 permitAll）
- 不实现 Region API client（仅留空包 `infrastructure/region`）
- 不实现 K8s 客户端封装（仅留空包 `infrastructure/k8s`）
- 不挂全局响应 ControllerAdvice、不挂全局异常处理（留给 `migrate-console-response-contract`）
- 不做 GraalVM Native 配置（留给最后一个 change `enable-graalvm-native`）
- 不实现 `/openapi/v1/*` 任何路径
- 不开 CORS（避免后续被默认配置坑住）

## Decisions

### 决策 1：Maven 单模块工程，Java 21，Spring Boot 4.0.6

**选择：** 单 module；`groupId=cn.kuship`，`artifactId=kuship-console`，`packaging=jar`。

**理由：** 项目早期，模块边界尚未稳定；多模块拆分（如 `console-core` / `console-api` / `console-region-client`）等后续 epic 推进到 region client 落地后再做。

**备选：** 多模块（被否决，理由：过早抽象，会拖慢首版；后续可平滑拆分）。

**Java 21 关键收益：** 虚拟线程对 console 这种"大量调 Region API、短任务、IO 密集"场景非常合适；模式匹配/record patterns 简化 DTO 操作。

### 决策 2：与 rainbond-console 共享 MySQL `console` 库

**选择：** JDBC URL `jdbc:mysql://127.0.0.1:3306/console`，dev 凭据 `root/123456`，`hibernate.ddl-auto=validate`，Flyway `baseline-on-migrate=true` 且 migration 目录留空。

**理由：** 双写并行迁移模式 —— kuship-console 与 Django 版同时运行，按 path 灰度切流；零数据迁移风险；可对照 Django 版接口验证 Java 实现的正确性。

**备选：** 独立新库 + 数据同步（被否决，理由：rainbond Go 集群也写这套表，分库后 Go 服务的写入 console 看不到，破坏多方契约）。

**关键约束：**
- schema 演进权属于 Django 那侧，kuship-console 永远不下发 DDL
- Flyway 仅作为"占位+防御性配置"，**migration 目录不放任何业务表 SQL**；只有当 kuship 自身需要"非共享的辅助表"（例如 Java 端独有的审计/锁表）时才考虑使用
- 不擅自给 entity 加 `@Version` 列（会写入 Django 不认识的字段）

### 决策 3：JPA + Hibernate + QueryDSL（不用 MyBatis Plus）

**选择：** `spring-boot-starter-data-jpa`（带 Hibernate 6.x）+ `querydsl-jpa` + `mapstruct`（DTO 映射）。

**理由：**
- Spring Boot 4 AOT 对 Spring Data JPA 处理已成熟（Repository 接口在编译期生成），Native 友好度高于 MyBatis Plus
- `validate` 模式天然守护共享 schema
- QueryDSL 处理复杂动态查询（替代 Django queryset），避免 Specification 嵌套地狱
- MapStruct 编译期生成 mapper，无运行时反射

**备选：** MyBatis Plus（被否决，理由：动态代理/反射多，自动扫描 mapper 的逻辑在 native 下需大量手工 hint）。

**JPA 命名策略：** 显式配置 `org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl`（保持原样不转换），实体字段用 `@Column(name="...")` 显式标注，避免 camelCase ↔ snake_case 自动转换的坑。

### 决策 4：URL 严格 `/console/*`，每个 Controller 显式前缀（不用 context-path）

**选择：** 不配 `server.servlet.context-path`；每个 Controller 写完整 `@RequestMapping("/console/...")`。

**理由：** 根域下 rainbond-console 还有 `/`、`/install-cluster.sh`、`/app-server/*`、`/api/*`、`/openapi/*`，context-path 会一刀切死，未来代理这些路径会很痛苦。

**Trailing slash 兼容：** `WebMvcConfigurer` 中显式 `setUseTrailingSlashMatch(true)`（或对应 PathPattern 的兼容）。Django 默认 `APPEND_SLASH=True`，UI 请求中存在带斜杠和不带斜杠两种情况。

**路径变量名严格保留：** `{team_name}`、`{region_name}`、`{service_alias}`、`{app_id}` 等都按 Django 原样，不改成驼峰。

### 决策 5：响应包装格式 100% 兼容 Django 版

**Django 版格式：**
```json
{"code":200,"msg":"success","msg_show":"操作成功",
 "data":{"bean":{...},"list":[...], "...任意 kwargs": "..."}}
```

**Java 落地：** 提供 `ApiResult` POJO + `GeneralMessage` 静态工具方法。`data` 字段类型为 `Map<String,Object>`（保留 Python 版的"任意 kwargs"灵活性）。

**本 change 仅落"骨架"：** 工具类可用，但**不挂全局 `@ControllerAdvice`**。`HealthzController` 显式调用 `GeneralMessage.ok()`，作为示例和契约校验点。全局 wrapping 留给下一个 change（`migrate-console-response-contract`），那时一并处理异常映射、`msg_show` 国际化、`page/page_size/total` 分页字段名兼容等。

### 决策 6：GraalVM Native 留到最后一个 change

**选择：** V1 全部 JVM 模式（`eclipse-temurin:21-jre`）；最后一个 change `enable-graalvm-native` 集中处理。

**理由：** 早期业务还在快速变动，过早 native 化会让每次 debug 痛苦（构建慢、错误信息晦涩）；`io.kubernetes:client-java` 是已知最大 native 风险点（OpenAPI generated POJO 反射巨多），需要单独 epic 处理。

### 决策 7：包结构

```
cn.kuship.console
├── KuShipConsoleApplication.java       Spring Boot 启动类
├── config/                             Spring 配置类
│   ├── SecurityConfig.java             permitAll 占位
│   ├── JpaConfig.java                  命名策略、@EnableJpaAuditing 占位
│   └── WebMvcConfig.java               trailing slash、消息转换器
├── common/
│   ├── response/
│   │   ├── ApiResult.java              {code, msg, msg_show, data}
│   │   └── GeneralMessage.java         静态工厂 ok()/error()/bean()/list()
│   ├── exception/
│   │   └── ServiceHandleException.java 业务异常基类（占位，handler 在下个 change）
│   └── util/                           （留空）
├── infrastructure/                     基础设施层（本 change 仅留空包占位）
│   ├── jpa/                            BaseEntity/审计基础（下个 change 落）
│   ├── region/                         Region API client（独立 epic）
│   └── k8s/                            kubernetes-client 封装（rke2 阶段落）
├── modules/                            业务模块（本 change 不进任何代码）
│   ├── account/
│   ├── team/
│   ├── application/
│   ├── region/
│   ├── plugin/
│   ├── market/
│   └── ...                             仅在 README/CLAUDE.md 中列出预期模块
└── healthz/
    └── HealthzController.java          GET /console/healthz
```

### 决策 8：13 阶段迁移路线图（附录，本 change 仅交付 #1）

| 阶段 | Change 名 | 目标 |
|------|-----------|------|
| 1 | **init-kuship-console** | 工程骨架（**本 change**） |
| 2 | migrate-console-response-contract | 全局响应/异常 ControllerAdvice、JWT、TenantHeader 拦截器 |
| 3 | migrate-console-region-client | 移植 3850 行 regionapi.py → Java（按资源域拆接口） |
| 4 | migrate-console-account-team | user / team / tenant / perm / oauth |
| 5 | migrate-console-region-cluster | region / registry / k8s_attribute / k8s_resource / rke2 |
| 6 | migrate-console-application-core | group / app overview / app_config 全套 |
| 7 | migrate-console-app-create | source_code / image / compose / vm / kubeblocks / 第三方组件 |
| 8 | migrate-console-app-runtime | manage / event / monitor / pod / log / autoscaler |
| 9 | migrate-console-app-market | market / share / backup / helm_app |
| 10 | migrate-console-plugin | plugin / platform_plugin |
| 11 | migrate-console-misc | upgrade / message / webhook / mcp / file_upload |
| 12 | migrate-openapi-v1 | 对外 OpenAPI（`/openapi/v1/*`） |
| 13 | enable-graalvm-native | 集中 native 化、reflect-config、kubernetes-client hint |

每个 change 的范围严格限定到 1-2 个业务域，确保都能在 1-2 周内 archive。

## Risks / Trade-offs

- **共享数据库的并发写风险** → kuship-console 与 Django 版同时写同一张表，可能出现 lost update。Mitigation：本 change 不写任何业务表，问题留到具体业务 change 时按表评估（Django 版有 `update_time`/`update_at` 的就用乐观锁，没有的需评估业务并发概率）。
- **JPA `validate` 与 Django schema 不完全契合** → Hibernate 对某些字段类型（TEXT/LONGTEXT、JSON）的验证可能误报。Mitigation：本 change 不引入任何 entity，所以不会触发；下游业务 change 引入 entity 时一例一议，必要时降级到 `none` 并改用启动期自定义 schema 校验。
- **trailing slash 兼容在 Spring 6/PathPatternsParser 下行为变化** → Spring 6 默认不再做 trailing slash 匹配。Mitigation：本 change 通过 `WebMvcConfigurer` 显式开启；HealthzController 提供两个测试案例（带斜杠 / 不带斜杠都返回 200）。
- **`io.kubernetes:client-java` 仅"预置依赖"未实际使用** → 引入但不调用，可能造成 jar 膨胀。Mitigation：本 change 在 `pom.xml` 中加但用 `<optional>true</optional>` 或 `<scope>provided</scope>` 暂缓；rke2 阶段再正式开。
- **明文凭据进 git 风险** → `application-local.yaml` 含 `root/123456`。Mitigation：`.gitignore` 强制忽略 `application-local.yaml`；`application.yaml` 用 `${DB_USERNAME}/${DB_PASSWORD}` 占位；CLAUDE.md 中显式说明。
- **响应骨架不挂 ControllerAdvice 期间，业务 controller 可能写出非兼容响应** → 本 change 期间只有 1 个 healthz controller，风险可控；下游 change 必须先做完 `migrate-console-response-contract` 再开业务接口。

## Migration Plan

本 change 是新建工程，无在线迁移：

1. 创建 `kuship-console/` 目录与 Maven 骨架
2. 本机 `mvn clean package` 验证构建通过
3. 本机启动（要求 `127.0.0.1:3306` 有 rainbond-console 的 `console` 库），访问 `http://localhost:8080/console/healthz` 验证 200
4. 验证 `/actuator/health` 返回 UP
5. `docker build` 验证镜像可构建并启动

**回滚策略：** 删除 `kuship-console/` 目录即可，无副作用（不写库、不影响其他模块）。

## Open Questions

- groupId 最终用 `cn.kuship` 还是 `io.kuship`？（本 change 暂用 `cn.kuship`，可在 review 阶段调整）
- 后续模块是否要拆成多 Maven module？（建议第 3 个 change `migrate-console-region-client` 落地后再评估，那时 region client 会成为天然的拆分点）
- Spring Boot 4.0.6 与 Hibernate 版本的精确组合是否已锁定？（建议用 Spring Boot BOM，由其管理 Hibernate 版本）
