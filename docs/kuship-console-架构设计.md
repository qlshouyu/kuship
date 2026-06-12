# kuship-console 架构设计文档

> 用 Java / Spring Boot 重新实现 rainbond-console 服务端的可行性分析与目标架构
>
> 适用范围：仅重写最上层控制台后端（console）。下游 Rainbond region-api（Go 集群控制面）、前端 rainbond-ui / kuship-ui 均不改造，直接复用现有契约。
>
> 文档定位：① 测绘被复刻对象 rainbond-console 的现状与可行性；② 给出 kuship-console 的全新目标架构与待办差距。
>
> 最后更新：2026-06-11

---

## 0. 一句话结论

**完全可行，且不是"能不能做"的问题，而是"按什么契约做"的问题。**

Rainbond 本身就是 **控制台(console) ↔ region-api ↔ Kubernetes** 的三层架构。我们替换的只是最上层 console 的**实现语言**（Python/Django → Java/Spring Boot），对上游前端、对下游 region-api 的**协议契约保持完全不变**。换的是引擎，不是接口——这正是这套迁移在工程上成立的根本原因。

> 注：git 历史 **提交 `261d345`** 中曾有一版 648 个 Java 文件的 kuship-console 实现（已验证可行），随后被提交 `c26936b`（"docker"）整体删除——**当前 HEAD 不含任何 kuship-console 源码**。本轮按"全新设计"**全部重做，不参照旧实现作基线**；仅保留其沉淀的契约约束与经验教训于第 6、7 章。

---

## 1. 系统全景

```
┌────────────────────────────────────────────────────────────────────────┐
│                            浏览器 / 第三方                                 │
│   rainbond-ui (Ant Design Pro / dva)        kuship-ui (拷贝自 rainbond-ui)│
│         │  axios baseURL = /console/*              │                      │
└─────────┼───────────────────────────────────────  ┼──────────────────────┘
          │  HTTP  Authorization: GRJWT <jwt>        │  (同一套接口契约)
          ▼                                          ▼
   ┌──────────────────────────┐          ┌──────────────────────────────┐
   │  rainbond-console (旧)    │          │  kuship-console (新/本文档)    │
   │  Python 3.6 / Django 2.2 │  ══重写═> │  Java 21 / Spring Boot 4.0.6  │
   │  DRF 3.8 / drf-jwt        │          │  Spring Security / jjwt       │
   └───────────┬──────────────┘          └───────────┬──────────────────┘
               │     两者共享同一个 MySQL `console` 库  │
               │   ┌──────────────────────────────────┘
               ▼   ▼
        ┌──────────────────┐        HTTP REST /v2/tenants/{ns}/...
        │  MySQL `console`  │      ┌────────────────────────────────┐
        │  ~123 张业务表     │      │  region-api (Go, Rainbond 集群) │
        └──────────────────┘      │  真实下发到 Kubernetes           │
                                   └───────────────┬────────────────┘
                                                   ▼
                                            ┌─────────────┐
                                            │ Kubernetes  │
                                            │  (k3s/rke2) │
                                            └─────────────┘
```

**关键洞察**：console 是一个 **"元数据 + 编排 + 鉴权 + 代理"** 层。
- **持久化的业务定义**（团队、应用、组件、端口、卷、插件、市场模板……）存在 console 库；
- **运行时状态**（Pod、构建、伸缩、监控、日志……）实时向 region-api 拉取，console 不缓存；
- console 本身**不直接碰 Kubernetes**，所有集群操作都经 region-api 中转。

---

## 2. 被复刻对象 rainbond-console 现状测绘

### 2.1 接口契约规模

| 域 | 端点数 | 说明 |
|----|-------:|------|
| `/console/teams/{team_name}/...` | ~286 | 团队级：应用/组件配置、部署、网关、插件、市场、备份等（占 55%） |
| `/console/enterprise/{eid}/...` | ~105 | 企业级：用户/团队/区域/许可/监控/节点/应用库 |
| `/console/users/...` | ~14 | 登录、注册、令牌、详情 |
| `/console/{regions,market,hub,update,mcp,...}` | ~119 | 区域、镜像仓库、平台升级、MCP 等 |
| `/openapi/v1/...` | ~53 | 对外开放 API，独立 token 鉴权 + Swagger |
| **合计** | **~577** | View 文件 ~140 个，路由 ~1300 行 |

> 写接口约 430 个（POST 235 / PUT 109 / DELETE 87），其中绝大多数在 service 层调用 region-api 操作集群。

**接口风格**：DRF `APIView`（**非 ViewSet**），直接实现 `get/post/put/delete`，统一返回 `Response(general_message(...))`。鉴权与多租户上下文通过 **View 基类继承链** 注入（见 2.4）。

### 2.2 数据模型规模（共享 `console` 库）

~123 个 Django ORM 模型，按域归类：

| 域 | 代表表 | 关键点 |
|----|--------|--------|
| 账户/用户 | `user_info` / `user_access_key` / `user_oauth_service` | `user_id` 主键，`sys_admin` 超管标志 |
| 企业 | `tenant_enterprise` / `enterprise_user_perm` | `enterprise_id` char(32) |
| 团队/租户 | `tenant_info` / `tenant_region` / `tenant_perms` | `tenant_id` char(32/33)，1:1 映射 K8s namespace |
| 应用与组件 | `service_group` / `tenant_service` / `tenant_services_port` / `tenant_service_volume` / `service_domain` | 核心工作负载模型，~20 张表 |
| 应用市场 | `rainbond_center_app(_version/_tag)` / `service_share_record` | 模板发布与安装 |
| 插件 | `tenant_plugin` / `plugin_build_version` / `tenant_service_plugin_relation` | 中间件插件 |
| 区域/集群 | `region_info`(RegionConfig) / `region_app` / `rke_cluster` | 集群注册与本地↔region ID 映射 |
| 权限 RBAC | `perms_info` / `role_info` / `role_perms` / `user_role` | 权限码分段（见 2.4） |
| 升级/灰度 | `app_upgrade_record` / `gray_release_record` | |
| 备份迁移/导入导出 | `groupapp_backup` / `app_import_record` | |
| 监控/配置/K8s 属性 | `tenant_service_monitor` / `app_config_group` / `component_k8s_attributes` | |

**主键风格**：自增 `ID` + 业务 UUID（`tenant_id`/`service_id`/`enterprise_id`/`plugin_id` 均 char(32)）。
**隔离字段**：`tenant_id` + `region_name` + `enterprise_id`。
**⚠️ 关系实现**：Django ForeignKey 大量 `db_constraint=False`，**没有数据库外键**，靠应用层逻辑关联——这对 JPA 建模是关键约束（见 6.2）。

### 2.3 与 region-api 的通信契约

- **客户端**：`www/apiclient/regionapi.py` 的 `RegionInvokeApi`（~4000 行，400+ 方法），底层 `RegionApiBaseHttpClient`（urllib3 连接池）。
- **协议**：HTTP REST，路由前缀 `/v2/tenants/{region_tenant_name}/...`。
- **地址来源**：`region_info` 表（`url` / `wsurl` / `httpdomain` / `tcpdomain`）。
- **认证（优先级）**：① 企业级 Token `Token <token>`（多租户隔离）→ ② 区域级 `RegionConfig.token` / 环境变量 → ③ 双向 TLS（`ssl_ca_cert` / `cert_file` / `key_file`，路径或 PEM 内容均可）。`REGION_SSL_VERIFY` 控制校验，默认 false。
- **调用分组**：租户/应用/组件生命周期、构建、部署、伸缩、端口与网关、卷、环境变量、依赖、插件、探针、监控指标、日志、Pod/节点、Helm、导入导出、KubeBlocks、VM 等约 20 类。
- **可靠性**：默认重试 2 次；超时梯度 2s(轻查询)→10/15s(常规)→20s(构建)→300s(导入/备份)；按 URL+SSL 缓存 PoolManager。
- **数据边界**：console 存"定义/配置"，region 是运行时状态的 **Source of Truth**，每次实时查询。

### 2.4 鉴权、多租户与分层

**认证**：`POST /console/users/login`（form：`nick_name`+`password`）→ 校验后内部签发 JWT → 返回 `data.bean.token`。后续请求头 `Authorization: GRJWT <token>`（也兼容小写 `jwt` 前缀）。基于 `djangorestframework-jwt`，HS256；可选 Redis `JwtManager` 做会话/黑名单追踪。

**多租户三维**：`enterprise_id`（企业）/ `team_name`+`tenant_id`（团队）/ `region_name`（集群）。来源优先级：URL 路径 → Header(`X-Team-Name`/`X-Region-Name`) → Cookie → Query。

> **为什么大量写接口必须带 `?region_name=`**：同一团队可跨多个 region，写操作必须显式指定目标集群，否则 `400 请求参数不全`。这是 `RegionTenantHeaderView` 强制的。

**View 基类继承链**（上下文自动注入）：

```
APIView (DRF)
 ├─ BaseApiView / AlowAnyApiView           (匿名/开放)
 └─ JWTAuthApiView                          认证 + 企业权限
     ├─ EnterpriseHeaderView                注入 enterprise 上下文
     └─ TenantHeaderView                    注入 self.user/tenant/team + 团队权限
         └─ RegionTenantHeaderView          + self.region_name
             └─ ApplicationView             + self.app/service（组件级）
```

**RBAC 四层 + 权限码分段**：
- 表：`perms_info`(权限定义) / `role_info`(角色) / `role_perms`(角色→权限) / `user_role`(用户→角色)。
- 权限码段：`1xxxxx` 企业 / `3xxxxx` 应用 / `4xxxxx` 组件；团队所有者短路放行全部。

**分层（Service-Repository-RegionClient，均单例）**：

```
View(console/views/*.py)  →  Service(console/services/*.py, 单例)
                                   ├─ Repository(console/repositories/*.py, 单例) → Django ORM
                                   └─ RegionInvokeApi(单例) → region-api
```

**横切**：统一响应 `general_message(code,msg,msg_show,bean,list,**kwargs)`；自定义异常 `ServiceHandleException`（带 `msg_show` 中文 + `status_code`）；全局 `custom_exception_handler`；前端错误上报 `POST /console/errlog`；操作审计 `operation_log`。

---

## 3. 可行性论证

| 维度 | 评估 | 依据 |
|------|------|------|
| **契约稳定性** | ✅ 高 | 前端/下游协议不变，只换 console 实现语言 |
| **数据层** | ✅ 可行（需谨慎） | 共享同一 MySQL，JPA 反向映射既有 schema；无 DB 外键反而简化 JPA 关系 |
| **接口规模** | ⚠️ 大但有界 | ~577 端点可分域增量迁移，非一次性 |
| **下游集成** | ✅ 可行 | region-api 是稳定 HTTP REST，Java HttpClient 5 直接对接 |
| **鉴权互通** | ✅ 零成本 | 两端配置同一 `JWT_SECRET_KEY` + HS256，token 双向互认，前端无感 |
| **风险点** | ⚠️ 行为对齐 | 必须 1:1 复刻路径命名、响应信封、错误码、`region_name` 作用域语义 |

**核心可行性来自三条不可变契约的"可被精确复刻"**：URL 形状、响应信封、JWT 算法/密钥同源。只要这三者对齐，rainbond-ui 无需任何改动即可指向 kuship-console。

---

## 4. kuship-console 目标架构（全新设计）

### 4.1 技术栈

| 关注点 | 选型 |
|--------|------|
| 语言/运行时 | Java 21 |
| 框架 | Spring Boot 4.0.6（Spring MVC + Security 6 + Actuator） |
| 持久化 | Spring Data JPA + Hibernate 6 + QueryDSL（复杂查询） |
| 对象映射 | MapStruct（entity ↔ DTO） |
| DB 迁移 | Flyway（仅 baseline，**不输出业务 DDL**，schema 属权归 Django） |
| 鉴权 | Spring Security + jjwt（HS256，兼容 drf-jwt） |
| 下游客户端 | Apache HttpClient 5（region-api）；io.kubernetes client-java（rke2 阶段） |
| API 文档 | springdoc-openapi |
| 缓存 | Caffeine（本地） |
| 会话 | **Redis（首版必需）**：JWT 会话黑名单 / 强制下线（决策已定，见 §8） |
| 其它 | Lombok、BouncyCastle（证书）、aliyun-sms |

### 4.2 分层架构（DDD 风格）

```
┌─────────────────────────────────────────────────────────────┐
│ interfaces (Controller)   @RequestMapping("/console/...")     │
│   显式完整路径前缀 · 保留 snake_case 路径变量 · 返回裸 POJO     │
└───────────────┬─────────────────────────────────────────────┘
                │  GeneralMessageResponseBodyAdvice 自动包信封
┌───────────────▼─────────────────────────────────────────────┐
│ application (业务服务)   事务边界 · 编排 repository + region    │
└───────┬───────────────────────────────────┬─────────────────┘
        ▼                                   ▼
┌───────────────────┐            ┌──────────────────────────────┐
│ domain + infra/jpa │            │ infrastructure/region         │
│ JPA Entity/Repo    │            │ RegionClient (HttpClient5)    │
│ → MySQL console    │            │ → region-api /v2/tenants/...  │
└───────────────────┘            └──────────────────────────────┘
        │                                   
        └── infrastructure/k8s (client-java, rke2 阶段)
```

### 4.3 包结构

```
cn.kuship.console
├── KuShipConsoleApplication
├── config/                  SecurityConfig · JpaConfig · WebMvcConfig
├── common/
│   ├── response/            ApiResult · GeneralMessage · GeneralMessageResponseBodyAdvice · @SkipResponseWrapper
│   ├── exception/           ServiceHandleException · GlobalExceptionHandler
│   ├── security/            JwtAuthenticationFilter · JwtClaims · 401/403 handler
│   ├── context/             RequestContext(@RequestScope) · TenantContextInterceptor
│   ├── page/ trace/ util/
├── infrastructure/
│   ├── jpa/                 BaseEntity / 审计字段
│   ├── region/             RegionClient + 各域 region 调用封装
│   └── k8s/                 kubernetes client-java 封装
├── modules/                 按业务域切分（account/team/application/appmarket/
│                            region/plugin/gateway/appruntime/appcreate/
│                            grayrelease/openapi/misc/thirdparty ...）
│                            每个域内: controller / service / entity / repository / dto / mapper
└── healthz/                 GET /console/healthz
```

### 4.4 关键横切设计

**响应信封自动包装**（对齐 `general_message`）：
- `GeneralMessageResponseBodyAdvice` 自动把返回值包成 `{code,msg,msg_show,data:{bean,list,...}}`；
- 映射规则：POJO/Map→`data.bean`；`List`→`data.list`；`Page`→`data.list=content`+`data.bean.total`；`ApiResult` 幂等不重包；
- `String` 返回不自动包装（Spring 特殊处理）；`@SkipResponseWrapper` 用于 SSE/文件下载。

**全局异常映射**（HTTP 状态码 = 业务 code，对齐 DRF）：
| 异常 | code |
|------|-----:|
| `ServiceHandleException` | 透传 |
| 参数校验类（`MethodArgumentNotValid`/`ConstraintViolation`/...） | 400 |
| Region 异常 | 优先其 httpStatus，否则 code 或 500 |
| 兜底 `Exception` | 500（body 带 `data.bean.trace_id`） |

**JWT 认证**（兼容 djangorestframework-jwt 1.11.0）：
- 接受 `GRJWT`(主) 与 `jwt`(兼容) 前缀，大小写不敏感；HS256；
- `JWT_SECRET_KEY` **必须与 rainbond-console 同源**（非 local profile 启动时为空则拒绝启动）；
- payload 直用 Django 风格 claims：`user_id`/`username`/`nick_name`/`email`/`exp`/`orig_iat`；
- token 中 `user_id` 必须真实存在于 `user_info` 表，否则 401 `user not found`；
- 401 `msg` 暴露具体原因，`msg_show` 统一中文文案。
- **Redis 会话黑名单（首版必需，决策已定）**：每请求在验签通过后查 Redis 黑名单（命中即 401）；登出/强制下线把 token（或 `user_id`+`orig_iat`）写入黑名单，TTL 对齐 token 剩余 `exp`。对应旧版可选的 `JwtManager`。

**请求上下文** `RequestContext`(@RequestScope)：`JwtAuthenticationFilter` 真实加载 user；`TenantContextInterceptor` 从路径变量 `{team_name}`/`{region_name}` 写入 `teamName`/`regionName`。

**Region 客户端**：`RegionClient` 封装 HttpClient5，从 `region_info` 表取地址与凭证，支持 token / 双向 TLS、重试与超时梯度，按域拆分调用方法。

---

## 5. 不可变契约清单 ⛔（新设计的硬约束）

> 这些由 rainbond-ui 和共享 `console` 库**客观决定，不可更改**。违反任意一条都会破坏前端或数据库兼容。

1. **共享 console 库**：`hibernate.ddl-auto=validate`，任何环境不输出 DDL；schema 演进归 Django migrations；**不得在 entity 加 `@Version`** 等 Django 不认识的列。
2. **URL 契约**：不使用 `server.servlet.context-path`（根域下还有 `/openapi`、`/app-server`、`/api` 等）；每个 controller 显式声明完整 `/console/...` 前缀；**路径变量保持 snake_case**（`{team_name}`/`{region_name}`/`{service_alias}`/`{app_id}`），不得改驼峰；trailing slash 在 controller 显式兼容。
3. **响应信封**：顶层字段顺序 `code→msg→msg_show→data`；`msg_show` 用 `@JsonProperty("msg_show")` 强制 snake_case；`data` 必含 `bean`(默认`{}`) 与 `list`(默认`[]`)。
4. **JWT 同源**：与 rainbond-console 同一 `JWT_SECRET_KEY` + HS256，claims 不做名字转换，实现双向互认。
5. **`region_name` 作用域**：region 作用域写接口必须接受 `?region_name=`，缺失返回 400，语义与 `RegionTenantHeaderView` 一致。
6. **错误码语义对齐**：业务 `code` 与 HTTP 状态码对齐，前端可直接复用 rainbond-ui 的 `request.js`（按 HTTP 状态进 axios catch + 全局 toast）。

---

## 6. 上一版（HEAD 中 648 文件实现）经验沉淀

> 已被故意删除、本轮重做，但以下认知应保留，避免重蹈。

### 6.1 已验证可行的设计
HEAD 版已落地并验证：契约层（响应/异常/JWT/分页/TraceId）+ DDD 分层（modules/infrastructure/common）+ 自动响应包装 + JWT 双前缀兼容 + RequestContext 多租户上下文。第 4、5 章的设计正是这套经验的提炼。已迁移到一定规模的域：application(72)、account(66)、appmarket(60)、region(39)、plugin(35)、gateway(35)、appruntime(33)。

### 6.2 数据层教训
- console 库**无 DB 外键**（Django `db_constraint=False`）→ JPA 关系用 `@ManyToOne(...) @JoinColumn(..., foreignKey=@ForeignKey(NO_CONSTRAINT))` 或干脆存外键 ID 字段 + 手动关联，**不要依赖级联**。
- 主键混用自增 `ID` 与业务 char(32) UUID → entity 需明确区分 DB 主键与业务标识。
- `validate` 模式下，entity 字段必须与既有列**精确**对齐（命名策略、类型、可空性）。

### 6.3 接口实测发现（对接校准，来自 `docs/接口实测发现.md`）
- 部分接口方法/入参与直觉不符：`/teams/{team}/modifyname` 实为 **POST**（非 PUT）；`access-token` 的 `age` 必须数字；`certificates` 必须带 `certificate_type`。
- rainbond 该版本存在**真实后端 bug**（如 `registry/auth` 缺参、favorite/access-token 的 DELETE NameError、未绑集群时 plugins 的错误处理 bug）——复刻时应**修正而非照搬 bug**，但需保持正常路径的出参结构一致。

---

## 7. 迁移策略与待办差距

### 7.1 增量迁移原则
- **按域纵切**，不按层横切：每次完整迁移一个业务域的 controller→service→entity→region 调用，可独立联调。
- **契约优先**：先固化第 5 章契约层（响应/异常/JWT/上下文/region 客户端骨架），再灌业务域。
- **以 rainbond-ui 实际调用为验收**：kuship-ui 指向 kuship-console，逐域对比 rainbond-console(7070) 与 kuship-console(8000) 的响应。

### 7.2 域迁移优先级（建议）
```
P0 契约层 + 健康检查 + 登录鉴权(users/login, JWT)        ← 一切前提
P1 account/team(团队/成员/角色/权限) + enterprise 基础     ← 进入控制台
P2 region(集群注册/配置) + RegionClient 骨架               ← 打通下游
P3 application + 组件配置(env/port/volume/domain/probe)    ← 核心工作负载
P4 appcreate(源码/镜像/compose) + appruntime(启停/部署/伸缩)
P5 gateway / plugin / appmarket / grayrelease / 备份迁移
P6 openapi/v1 + monitor + 杂项(errlog/announcement/...)
```

### 7.3 当前状态
- 工作区 `kuship-console/` 已清空；**本轮全部重做，不参照任何旧实现作基线**（用户已明确）；
- 旧版 648 文件实现在提交 `261d345`，已被 `c26936b` 删除；**HEAD 不含 kuship-console 源码**，不作参考基线；
- `docs/` 已有 `接口实测发现.md`（实测校准）与本架构文档。

---

## 8. 风险与未决问题

| 风险 | 说明 | 缓解 |
|------|------|------|
| 行为对齐偏差 | 577 端点的出参字段/错误码细节难以全覆盖 | 以 rainbond-ui 实调 + 7070 对照为准；建契约测试 |
| Django ORM 隐式行为 | 软删除、信号、`auto_now`、JSON 字段语义 | 逐表核对，必要时保留 Django 行为 |
| region-api 版本耦合 | RegionInvokeApi 400+ 方法随 Rainbond 版本演进 | 钉死参照的 Rainbond 版本；RegionClient 按需迁移 |
| 共享库并发写 | rainbond-console 与 kuship-console 可能同时写同库 | 明确切流策略：一个团队/域只由一端负责写 |
| Redis JwtManager | 是否需要会话黑名单 | 评估；可先无状态 JWT，后续按需加 |

### 决策记录（2026-06-11 已定）
- ✅ **`/openapi/v1` 推迟到后期**：首版只做 `/console/*` 内部控制台接口，先让 rainbond-ui 完整跑通；openapi 独立 token 体系单独一轮。
- ✅ **完全替换，旧 rainbond-console 停掉**：kuship-console 单独负责写 `console` 库，无并发双写问题（消除原"共享库并发写"风险项）。
- ✅ **Redis 会话黑名单（首版必需）**：非纯无状态；引入 Spring Data Redis，JWT 过滤器每请求查黑名单、登出写黑名单（见 §4.1 / §4.4）。

- ✅ **单 region-api 对接优先**：首版只打通**单 region-api**，多 region / rke2 多集群管理推迟到后期；RegionClient 骨架按单 region 设计，预留多 region 扩展点。
- ✅ **兼容基线钉死 Rainbond v6.9.0-release**：以 `reference/rainbond` 子模块当前 commit **`44c5c34d`**（`v6.9.0-release-1-g44c5c34da`，2026-06-08）为 region-api 契约的唯一兼容基线；RegionClient 各域方法按此版本复刻。

---

## 附录：关键参考文件（reference/rainbond-console）

| 主题 | 路径 |
|------|------|
| 路由 | `console/urls/__init__.py`、`openapi/urls.py` |
| View 基类 | `console/views/base.py`、`console/views/app_config/base.py` |
| 响应格式 | `www/utils/return_message.py`（`general_message`） |
| 数据模型 | `console/models/main.py`、`www/models/main.py`、`www/models/plugin.py` |
| region 客户端 | `www/apiclient/regionapi.py`、`regionapibaseclient.py` |
| 权限定义 | `console/utils/perms.py` |
| 异常 | `console/exception/main.py`、`goodrain_web/middleware.py` |
| 分层示例 | `console/services/team_services.py`、`console/repositories/team_repo.py` |
