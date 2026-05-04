## Context

- 已落地：账户/团队/RBAC、集群管理、Region API 客户端基础设施 + `TenantOperations` 5 method + `ClusterOperations` 8 method 完整实现
- 当前 kuship-console **没有任何业务"组件"端点** —— 用户登录后只能看到 team 与 region，看不到任何应用
- rainbond 端应用核心约束：
  - "组件 = `tenant_service` 表"（~50 列大表）；"应用 = `service_group` 表"（service_group 包含多个 tenant_service，通过 `service_group_relation` 关联）
  - 6 类子资源（env/port/volume/dependency/probe + 域名）每类都有独立的 Django app + 独立 region API endpoint
  - 大多数子资源的修改需要"两写"：写本地表 + 调 region API 通知集群同步配置；rainbond 历史选择本地写优先（写库失败立刻回滚；region 端失败则告警运维但不回滚本地）
- 14 接口骨架已搭好但其中 5 个（Service/Env/Port/Volume/Dependency/Probe）仅含 default `unsupported`，本 change 完成关键 method 实现

## Goals / Non-Goals

**Goals:**

1. 用户登录后能在前端看到"应用列表 / 组件列表 / 组件详情 / 6 类配置 tab"完整数据
2. 已存在组件可通过 console 编辑 envs/ports/volumes/dependencies/probes 并同步到 region
3. ServiceOperations + 5 个子资源 Operations 实现 ~25 个核心 method（覆盖 add/remove/list/get/update 五大动作）
4. 严格按 rainbond schema 落 entity，hibernate validate 启动通过

**Non-Goals:**

1. 应用创建（source_code/image/compose/vm/kubeblocks/3rd-party 6 种来源 → app-create change）
2. 域名/证书/网关策略（独立 gateway change，rainbond 端 1521 LOC，HTTP/TCP rule 体系独立）
3. 启停/构建/升级/回滚/scale（→ app-runtime）
4. 监控/日志/event/pod（→ app-runtime）
5. 插件配置（→ plugin change）
6. 多组件批量创建 / 克隆 / 迁移（→ misc）
7. 应用市场 / 分享（→ app-market）

## Decisions

### 决策 1：TenantService entity 如何落 50+ 列大表

**问题**：rainbond `tenant_service` 表含 50+ 列（含 docker_cmd / dockerfile / git_full_name / build_strategy / lang / 各种状态字段），如果一次性把 50 个字段都映射成 entity 字段，本 change 会变成 entity-only PR（无业务）。

**选择**：
- 一次性把所有 50+ 列都映射进 `TenantService` entity（`@Column` 显式列名 + `Integer/String/Boolean` 主类型），但**业务方法只 setter/getter 需要的子集**
- 不需要在本 change 触及的字段（git/docker/code/dockerfile/build_*）按"普通 setter/getter"暴露，但 controller 不调用，等 app-create change 用
- 字段类型对齐：tinyint(1) → Boolean、varchar/longtext → String、int → Integer
- `protocol` 字段在 `tenant_service` 与 `tenant_services_port` 表都有，命名空间冲突 —— 各自的 entity 独立属性，不混用

**Rationale**：避免本 change 与 app-create 之间反复扩 entity；hibernate validate 一次性通过；setter/getter 是 Lombok 自动生成的零成本抽象。

### 决策 2：写操作的两写一致性策略

**问题**：env/port/volume/dependency 的写入需要同时写本地表 + 调 region API（K8s ConfigMap / Service 等）。任一失败如何处理？

**选择**：
- 顺序：**先调 region API**（成功后再写本地表，事务包裹）—— 失败时本地表无副作用，简单
- 例外：env 修改不需要 region API（rainbond 端 env 仅在重启时通过 region 重新读 console DB），所以纯本地事务
- region API 调用失败抛 `RegionApiException` —— 由 `GlobalExceptionHandler` 包装为 general_message 响应，错误码透传
- 本地写失败但 region 已写成功 → 本 change 选择 "let it fail"（rainbond 历史行为）：日志告警 + 错误返回告知用户"集群与 console 状态可能不一致，请联系运维"，不做 region 端 rollback

**Rationale**：先 region 后本地的好处是失败时本地无脏数据；与 rainbond Python 端选择一致

**Alternatives considered**：
- ❌ 先本地后 region：region 失败时本地有脏数据，需要补偿事务
- ❌ 引入 saga / outbox 模式：本 change 范围内 over-engineering

### 决策 3：组件归属（group/app_id）的关系建模

`tenant_service` 表本身有 `tenant_service_group_id` 字段，但实际应用归属通过 `service_group_relation` 表（service_id ↔ group_id N:N）维护。

**选择**：
- 引入 `ServiceGroupRelation` entity 作为 application ↔ service 关联（与 rainbond 一致）
- 查询"应用下所有组件"通过 `serviceGroupRelationRepo.findByGroupId(appId)` → 拿到 service_id 列表 → batch query
- 组件迁移到另一应用：先 delete 旧关联 → insert 新关联（事务包裹，不写 `tenant_service.tenant_service_group_id`，那个字段是历史字段，不维护）

**Rationale**：rainbond Python 端实际维护 `service_group_relation`，`tenant_service.tenant_service_group_id` 是冗余字段；与 rainbond 一致避免双向同步

### 决策 4：probe 唯一性约束（liveness / readiness / startup）

rainbond `service_probe` 表无 unique constraint，但业务上"每种 mode 每个组件只能有一条" —— rainbond Python 端在 service 层做软去重（先查同 service_id+mode → 删除 → 插入）。

**选择**：本 change 沿用软去重策略（`AppProbeService.upsert(serviceId, mode, ...)` → 先 delete 同 service_id + mode → 再 insert）。事务包裹。

### 决策 5：本 change 的 ServiceOperations 实现到什么程度

**问题**：`ServiceOperations` 接口（migrate-console-region-client 阶段声明了 20+ 个 method）含 build/code_check/upgrade/scale 等不在本 change scope 的 method。

**选择**：
- `ServiceOperations` 接口不修改（保留所有 method 的 default unsupported）
- 仅实现 5 个核心 method 在 `ServiceOperationsImpl`（`@Primary @Service`）：getServiceStatus / getServiceDetail / batchGetServicesStatus
- 子资源 Operations（5 个：Env/Port/Volume/Dependency/Probe）：每个实现 ~5 个 method（add/remove/list/get/update），全部用 `@Primary @Service` Impl 类
- 其余 method 仍 unsupported；后续 change（app-create / app-runtime）补完

**Rationale**：渐进式补完接口骨架；本 change scope 边界对齐 spec.md 的"6 类配置子资源"

### 决策 6：governance_mode 的处理

rainbond `service_group.governance_mode` 字段控制服务治理模式（KUBERNETES_NATIVE / RAINBOND_NATIVE_SERVICE_MESH / NO_GOVERNANCE / SERVICE_MESH 等枚举值）。

**选择**：
- 仅落"读 + 写"端点，不复刻 rainbond 复杂的 governance check（governance-cr / check 端点 punt）
- 写入时只校验值在已知枚举内（`KUBERNETES_NATIVE` / `RAINBOND_NATIVE_SERVICE_MESH` / `BUILD_IN_SERVICE_MESH` / `NO_GOVERNANCE`）；不调 region API
- governance-cr / governance-check 等高级端点 punt

### 决策 7：env / port / volume 的请求体形状

rainbond 前端发送的请求体格式（envs 为例）：
```json
{
  "name": "DB_HOST",
  "attr_name": "DB_HOST",
  "attr_value": "172.20.0.10",
  "is_change": true,
  "scope": "inner"
}
```

**选择**：
- DTO record 每字段对应 rainbond 形状（`@JsonProperty` 显式映射）
- `name` 是用户可见的描述（与 attr_name 可不同）；attr_name 是实际环境变量名
- 简化：本 change 不验证 attr_name 合法性（rainbond 不强校验）

## Risks / Trade-offs

- **[TenantService 50+ 列 entity]** 字段多 → 每个 service 查询 SELECT * 单行 ~3KB，N+1 容易爆。
  - **Mitigation**：列表查询返回 `TenantServiceBriefDto`（仅 service_id/service_alias/service_cname/k8s_component_name/state ~7 字段），用 JPQL projection 避免 SELECT *
- **[region API 调用阻塞]** 写 port / volume 时调 region API → 默认 5s timeout，慢 region 影响响应。
  - **Mitigation**：保持 5s，文档化此风险；不引入异步（与 rainbond 一致）
- **[两写不一致]** region 写成功但本地写失败 → 状态不一致。
  - **Mitigation**：日志告警 + 响应明确告知；hardening change（独立的 reconciliation job）解决
- **[probe 软去重的并发风险]** 两个并发 PUT 同 mode 可能同时通过去重检查 → 双行写入。
  - **Mitigation**：事务隔离级别 REPEATABLE_READ + 一致性校验（写入后 SELECT 验证只剩一条）；本 change 接受小概率风险，同 rainbond
- **[service_group_relation 双向维护]** rainbond 双写关联表与 tenant_service.tenant_service_group_id 字段历史不一致。
  - **Mitigation**：本 change 仅维护 service_group_relation，文档化 tenant_service_group_id 字段被忽略

## Migration Plan

1. PR 1：7 个 entity + 7 个 repository（无业务）
2. PR 2：`ServiceOperationsImpl` + `ServiceEnvOperationsImpl` + `ComponentController`（detail/brief/status/group）+ `AppEnvController`
3. PR 3：`ServicePortOperationsImpl` + `AppPortController`
4. PR 4：`ServiceVolumeOperationsImpl` + `AppVolumeController`
5. PR 5：`ServiceDependencyOperationsImpl` + `AppDependencyController`
6. PR 6：`ServiceProbeOperationsImpl` + `AppProbeController`
7. PR 7：`GroupController` + 集成测试 + 文档

无 DB 迁移；本 change 不发任何 DDL。

## Open Questions

- **配置组（config_groups）** —— rainbond 用于多组件共享配置，本 change punt（独立 small change）
- **service.label / extend_method** —— 本 change punt（属于 app-create 或 misc）
- **monitor_rule（service-monitor）** —— punt 到 app-runtime
- **lang_version 的 region 同步** —— 仅做读端点透传，写端点等 app-create 一并落
