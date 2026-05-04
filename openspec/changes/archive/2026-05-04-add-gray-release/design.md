## Context

rainbond-console 的灰度发布是一个**重业务能力**：单一 service 1327 行代码统揽了模板实例化、域名权重计算、ApisixRoute CRD 调用、状态机维护、回滚清理。直译为 Java 容易复刻其耦合问题；但 kuship-console 已提供两类抽象可以直接复用：

- **`AppInstallService`** (in `modules/appmarket`)：从应用模板创建 service group 的通用流程（迁移自 `market_app_service.install_app`）
- **`RegionClient`** (in `infrastructure/region/client`)：HTTP 客户端，对 rainbond-go core 的 `/v2/tenants/...` 与 `/api-gateway/v1/...` 接口都已抽象

所以本 change 不重写 1327 行 Python 逻辑，而是把 Python service 拆分为 3 个 Java 协作组件 + 委托既有抽象。

`gray_release_record` 表已存在共享 console DB（rainbond migration 0002）；kuship-console 走 `ddl-auto=validate`，无 schema 风险。

## Goals / Non-Goals

**Goals:**
1. 1:1 兼容 rainbond-console 4 个 OpenAPI v1 端点 + 1 个 console 内部端点的请求/响应包结构
2. 状态机（active → completed / cancelled）原子推进，不允许同 app 并发出现 2 个 active 记录
3. ApisixRoute 操作通过 RegionClient 委托 go core，**不**在 kuship-console 直接持有 K8s client
4. GraalVM native binary 下端点可用（反射 hint 注册到 KuShipConsoleRuntimeHints）
5. 集成测试覆盖 happy path + 4 类失败（ratio 越界、并发 active、回滚不存在记录、go core 5xx）

**Non-Goals:**
- **基于 header / cookie 的灰度匹配规则**（rainbond 当前仅支持权重分流，本 change 保持等价；高级匹配规则留给 `add-grayrelease-header-routing` 单独 change）
- **A/B 实验埋点 / 流量画像**（留给 `add-grayrelease-analytics`）
- **删除 rainbond-console 对应代码**（rainbond-console 仍作为 reference 留存，本 change 不动）
- **修改 `gray_release_record` 表 schema**（即使有冗余字段如 `service_mappings` JSON，迁移时保持一致）
- **重写 `AppInstallService`** 或其他既有 module —— 仅消费它们暴露的 public API

## Decisions

### Decision 1: Service 拆 3 而非单类直译

rainbond Python `gray_release_service.py` 1327 行混合了：参数校验、模板实例化编排、域名权重计算、HTTP 调用、状态机管理。Java 端拆为 3 类各自单职责：

| 类 | 职责 | 行数预估 |
|---|---|---|
| `GrayReleaseService` | 状态机 + 业务编排（公开 API） | ~400 |
| `ApisixRouteWeightUpdater` | (domain, original_svc, gray_svc, ratio) → RegionAPI HTTP 调用 | ~200 |
| `GrayReleaseTemplateInstaller` | 调 `AppInstallService` 安装灰度版本服务组（封装 skip_create_domain=true 等开关） | ~150 |

**Why:** 三个职责的失败模式不同（业务校验 = 400/422、HTTP = 502/504、模板实例化 = ServiceHandleException），分开后每类有独立的 try/catch 与重试策略，集成测试也可分别 mock。

**Alternative considered:** 直接 1:1 拆 Python `gray_release_service.py` 的 30+ 私有方法为 Java 私有方法。**否决**：复刻 1300+ 行 god class 不利于后续 hardening（如 add-grayrelease-header-routing 改路由匹配规则需重写大段代码）。

### Decision 2: 状态机用 enum + DB 唯一约束 而非乐观锁

`GrayReleaseStatus` enum：`ACTIVE` / `COMPLETED` / `CANCELLED`。

并发约束："同一 (tenant_id, app_id) 不允许 2 个 ACTIVE 记录"。实现方式：

- **首选**：依赖 DB unique index `(tenant_id, app_id, status)` —— 但需检查 rainbond migration 0002 是否建过；查 migration 文件只看到 `(tenant_id, app_id)` 与 `(tenant_id, status)` 两个**非唯一** index。
- **退而求其次**：在 `GrayReleaseService.create_gray_release` 入口先 `findByTenantIdAndAppIdAndStatus(ACTIVE)`，如果存在则抛 409 ConflictException；该路径不防真正并发（两个请求同时通过校验），但实际应用层并发极小（同一 app 灰度发布是人工触发，不是自动化批量调用）。
- **不引入乐观锁**：rainbond Python 端也没有乐观锁，引入 `@Version` 会让 spec 与 rainbond 行为产生 drift。

**Why:** 对齐 rainbond 行为优先；DB 层唯一性以后做 schema migration 时再加（独立 hardening `enforce-grayrelease-uniqueness`）。

### Decision 3: ApisixRoute 调用走 RegionClient 而非新增 K8s SDK

rainbond-go core 已暴露 `/api-gateway/v1/{tenant_name}/routes/http` 端点，由 go 端处理 ApisixRoute CRD 操作。kuship-console 在 `infrastructure/region/client/RegionClient` 已有 `apiGatewayPostProxy(region, tenantName, path, body, appId)` 风格 API（与 rainbond Python `region_api.api_gateway_post_proxy` 等价）。

**Decision:** `ApisixRouteWeightUpdater` 仅生成 PUT body 并调 `RegionClient.apiGatewayPostProxy`，不引入 fabric8 / k8s-client / java-operator 等依赖。

**Why:**
- 不增加 native binary 体积（fabric8 反射 hint 多，对 GraalVM 不友好）
- 不复制 rainbond-go core 已实现的 ApisixRoute 渲染逻辑（hosts / rules / plugins / authentication 等字段）
- 与 rainbond-console 行为对齐，go core bug fix 同时惠及 kuship-console

**Alternative considered:** 直接 fabric8-kubernetes-client 操作 ApisixRoute CRD。**否决**：复制 go core 1000+ 行 CRD render 逻辑成本巨大；本 change 范围爆炸。

### Decision 4: 模板实例化复用 AppInstallService 而非重写

灰度发布需要从同一应用模板再创建一组 service（灰度组），rainbond Python 是调 `market_app_service.install_app(skip_create_domain=True, upgrade_group_id=<new>)`。

kuship-console 的 `AppInstallService` 已迁移自 market_app_service（见 `2026-05-02-migrate-console-app-market` archive）；本 change 仅需通过 `GrayReleaseTemplateInstaller` 调 `AppInstallService.install` 并传 `skip_create_domain=true` 与新 `upgrade_group_id`。

**Why:** AppInstallService 已通过 `migrate-console-app-market` 测试，行为对齐 rainbond；重写一遍只为灰度场景纯属浪费且引入 drift 风险。

**Risk if AppInstallService 没暴露 skip_create_domain 参数：** 本 change 在 tasks.md task 4.3 验证；若没有，回退方案是在 AppInstallService 加可选 boolean 参数（不破坏既有调用），并在本 change tasks 加一项小改动。

### Decision 5: OpenAPI 端点走既有 OpenApiAuthFilter，console 端点走 JWT

4 个 `/openapi/v1/...` 端点继承既有 OpenAPI v1 端点鉴权链（apikey 校验 + 团队权限 + region 权限），与 `OpenApiOtherController` / `OpenApiAppController` 等同。SecurityConfig 不需要新加 permitAll，因 `/openapi/**` 已经 permitAll、由 `OpenApiAuthFilter` 内部完成鉴权。

1 个 `/console/teams/.../gray-release-info` 端点走 JWT 默认认证，与 `appruntime` 模块端点一致。SecurityConfig 也不需要变更。

**Why:** 与既有迁移过的 OpenAPI v1 / console 端点完全对齐，零新增鉴权逻辑。

### Decision 6: GraalVM native 反射 hint 仅注册 GrayReleaseRecord

JPA 实体需要反射访问 getter / setter / column annotation；`KuShipConsoleRuntimeHints` 已注册其他 entity 模式，本 change 只追加一行 `MemberCategory.values()` for `GrayReleaseRecord`。

DTO（请求/响应包）走 Jackson 3，已通过 `spring.aot.enabled=true` AOT 自动注册（与既有 OpenAPI v1 controller 一致），不需手动 hint。

**Why:** 最小改动；不引入新模式。

### Decision 7: 测试策略 — 单元 + 集成两层覆盖

- **单元层**：`ApisixRouteWeightUpdaterTest` 用 `@MockitoBean` mock `RegionClient`，验证 PUT body 完整性（hosts / rules / new_backends 权重分配 / authentication 透传）。`GrayReleaseServiceTest` mock 三个协作类，验证状态机迁移与并发约束（active → reject second create）。
- **集成层**：`OpenApiGrayReleaseControllerIntegrationTest` 用 contract-test profile + LoggingRegionClient（已有 stub）覆盖 4 端点 happy path + 4 失败用例。

**不写真实 ApisixRoute CRD 测试**：那是 rainbond-go core 的范畴；kuship-console 只验证 HTTP 调用 contract 正确。

## Risks / Trade-offs

- **[gray_release_record 表与 rainbond migration drift]** → 检查 `application.yaml` 已设 `ddl-auto=validate`，启动时 schema 不一致会立刻 fail-fast；CI 集成测试触及该表会在第一次执行时暴露问题。
- **[RegionClient `apiGatewayPostProxy` 接口可能尚未在 kuship-console 抽象]** → tasks task 1.4 验证；若缺失则在 `RegionClient` 加该方法（约 30 行）。本 change 把这看作前置 task 而非新建 capability。
- **[同 app 并发创建 2 个 active 记录]** → Decision 2 接受；提供 hardening 路径 `enforce-grayrelease-uniqueness`。
- **[模板实例化失败（go core 502）后已写入的 GrayReleaseRecord 残留]** → 用 `@Transactional` 包裹 service-group 安装 + record 写入；模板实例化失败时事务回滚不留垃圾记录。但 ApisixRoute 权重更新跨服务边界，事务无法覆盖——若权重更新失败需手动重试 / 回滚（与 rainbond Python 行为一致，加 WARN 日志 + 提供 rollback 端点兜底）。
- **[1327 行 Python service 拆 3 后可能漏掉边界]** → 通过逐项对照 Python 公共方法名 + 整理出 9 个公共操作（create/update_ratio/rollback/get_info_by_service/get_info_by_upgrade_group/list_by_app/list_by_tenant/delete_record/setup_domain_weights）一一对应 Java 实现，写在 tasks 表里逐项 check。

## Migration Plan

1. **Phase A — 契约层**：JPA entity + Repository + Service skeleton + RegionClient apiGateway 方法（如缺）。Verify: 启动通过 + 单元测试 GREEN。
2. **Phase B — 端点层**：4 OpenAPI controller + 1 console controller + DTO + OpenAPI 注解。Verify: contract-test profile 集成测试 happy path GREEN。
3. **Phase C — 失败边界 + native 兼容**：ratio 边界、并发拒绝、go core 5xx 处理、native 反射 hint。Verify: `mvn test` 全 GREEN + `bash scripts/native-test.sh --quick` GREEN。
4. **Phase D — 文档**：CLAUDE.md 段落 + tasks 标完成。

**回滚路径**：本 change 仅新增 module + 端点，不改既有逻辑。如线上发现端点 bug，可通过 nginx / 反代把 `/openapi/v1/.../gray-release` 等 URL 短期路由回 rainbond-console，等 fix 后再切回。

## Open Questions

1. **`AppInstallService` 是否暴露 `skip_create_domain` 参数？** —— task 4.3 验证；若否，本 change 加一个小子任务在 AppInstallService 加可选参数。
2. **`gray_release_record.service_mappings` JSON 字段** —— rainbond Python 用它存"原始 service_id → 灰度 service_id"映射数组。Java 用什么序列化？建议 Jackson 3 `JsonType` 自定义 converter（`@Convert(converter = ...)`），而不是 String 字段+手动序列化（避免 controller 层重复 parse）。
3. **`OpenApiGrayReleaseController` 的 `gray_releases` 列表分页** —— rainbond Python 端实现是分页，确认 default page_size。Task 5.5 看代码确认。
