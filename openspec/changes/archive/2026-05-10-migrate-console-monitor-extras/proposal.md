## Why

`migrate-console-app-runtime` 在 `MonitorOperations` 接口中已落地 4 个 method（`query` / `queryRange` / `batchQuery` / `getServiceResources`），覆盖了 UI 组件级监控页的 Prometheus 风格查询路径。但路线图 P1 #17（Monitor 指标 + 资源中心事件）仍有 6 个 method 缺口：

1. `get_monitor_metrics` —— 通用 Prometheus 指标列表，UI"组件指标管理 / 自定义监控点"页与 enterprise 监控聚合（`/openapi/v1/enterprise/{eid}/monitor/*`）调用，缺它就只能显示空 list
2. `get_resource_center_events`（**监控视角**）—— 资源中心按 `namespace + service` 过滤 events，与 `migrate-console-cluster-extras` 落地的"集群级 events"是两套语义（前者按 K8s 资源对象 / Pod / Workload 维度过滤，后者拿全量集群 events）
3. `get_query_domain_access` / `get_query_service_access` —— 团队页"域名访问量排序 / 组件访问量排序"两个仪表卡片
4. UI 组件配置页"Service Monitor"（自定义监控点 CRUD）依赖 `tenant_service_monitor` 表的本地读写 —— rainbond 端走 `service_monitor` model（`console/models/main.py:1086`），kuship 端尚未映射 entity

清桩 + 补 entity 解锁三件事：

1. UI 组件指标 / 自定义监控点 / 团队访问量排序 3 个仪表卡片不再是空数据
2. 后续 `migrate-console-app-share` / `migrate-openapi-monitor-aggregate` 子 change 可直接复用 `MonitorOperations.getMonitorMetrics` 而不再各自拼 URL
3. `ServiceMonitor` entity 落地为后续"组件指标管理"controller（独立 `ComponentServiceMonitorController` 由跟进子 change 实现）解决 entity 反射依赖

`migrate-region-coverage-roadmap` 把这块归为 **P1 #3**（6 method + 1 entity，与 cluster-extras 的 events 边界明确解耦）。本 change 完整迁移 rainbond `regionapi.py:get_monitor_metrics / get_resource_center_events / get_query_data / get_query_range_data / get_query_domain_access / get_query_service_access` 6 段 region API 调用。

## What Changes

### 扩展 `MonitorOperations` 接口（既有 4 method + 新增 2 method + 调整 2 method 语义）

落地 `cn.kuship.console.modules.appruntime.api.MonitorOperations` 的 method 调整：

| 既有 / 新增 | method | 说明 |
|-------------|--------|------|
| 既有 | `query(regionName, tenantName, queryParams)` | rainbond `get_query_data`，已实现 |
| 既有 | `queryRange(regionName, tenantName, queryParams)` | rainbond `get_query_range_data`，已实现 |
| 既有 | `batchQuery(regionName, tenantName, queryParams)` | 现有 batch 拼接（rainbond `BatchAppMonitorQueryView` 内部用 `get_query_data` 合成），已实现 |
| 既有 | `getServiceResources(regionName, tenantName, serviceAlias)` | 组件资源 (cpu/memory/disk)，已实现 |
| 新增 | `getMonitorMetrics(regionName, tenantId, target, appId, componentId)` | rainbond `get_monitor_metrics`，URL `/v2/monitor/metrics?target=&tenant=&app=&component=` |
| 新增 | `getResourceCenterEvents(regionName, tenantName, queryParams)` | rainbond `get_resource_center_events`（监控视角），URL `/v2/tenants/{tenant}/resource-center/events?...` |
| 调整 | `queryDomainAccess(regionName, tenantName, queryParams)` | rainbond `get_query_domain_access`，独立 method（不与 `query` 混淆） |
| 调整 | `queryServiceAccess(regionName, tenantName, queryParams)` | rainbond `get_query_service_access`，独立 method |

`queryDomainAccess` / `queryServiceAccess` 在 region 端 URL 与 `query` 一致（`/api/v1/query`，Prometheus 标准），但在接口层独立成 method 是为：

- 让团队页"访问量排序"卡片的调用链不再走 `query` 通用路径（避免 future 拆 PromQL 注入逻辑时撞车）
- 业务语义独立（rainbond Python 端独立成 `get_query_domain_access` / `get_query_service_access` 两个 method，本 change 同步对齐）

### 新增本地 entity `ServiceMonitor`

新建 `cn.kuship.console.modules.appruntime.entity.ServiceMonitor`，映射 rainbond `tenant_service_monitor` 表（rainbond `console/models/main.py:1086`），8 列：

```text
ID                  INT (PK auto-increment)
name                varchar(64)        监控点名称
tenant_id           varchar(32)        团队 ID
service_id          varchar(32)        组件 ID
service_show_name   varchar(64)        展示名称
port                int                端口号
path                varchar(255)       监控路径
interval            varchar(10)        采集间隔（如 "10s"）
create_time         datetime           BaseModel 继承（rainbond Django 默认 auto_now_add）
```

unique_together: `(name, tenant_id)`。

新建 `ServiceMonitorRepository extends JpaRepository<ServiceMonitor, Integer>`，提供：

- `findByTenantIdAndServiceId(tenantId, serviceId)` —— 列出组件下所有监控点（用于"组件指标管理"页）
- `findByTenantIdAndName(tenantId, name)` —— unique 校验 / 单点详情查询
- `existsByTenantIdAndName(tenantId, name)` —— 创建前重名快速校验

**注意**：本 change 仅落地 entity + repository（path / port / interval 等字段全部映射，create_time 列为 datetime），**不**落地组件监控点 CRUD 的 controller 与 region 调用（`/v2/tenants/{tn}/services/{alias}/service_monitor` 系列 region method 待跟进子 change `migrate-console-component-service-monitor` 落地）。本 change 完成 entity 字段一致性 + JPA `validate` 模式启动通过 + repository 单测，作为后续 controller 子 change 的 foundation。

### 补全 controller endpoint（`AppMonitorController` 扩展）

在既有 `cn.kuship.console.modules.appruntime.controller.AppMonitorController` 上追加 4 个端点（既有 4 个 endpoint 不动）：

- `GET /console/teams/{team_name}/apps/{service_alias}/metrics` —— 组件级 Prometheus 指标列表（rainbond `urls.py:774 ComponentMetricsView`），调 `getMonitorMetrics(target="component", componentId=service.serviceId)`
- `GET /console/teams/{team_name}/regions/{region_name}/resource-center/events?...` —— 监控视角资源中心事件（监控页跳转），调 `getResourceCenterEvents`
- `GET /console/teams/{team_name}/region/{region_name}/sort_domain/query` —— 团队域名访问量排序（rainbond `urls.py:328 TeamSortDomainQueryView`），调 `queryDomainAccess`
- `GET /console/teams/{team_name}/region/{region_name}/sort_service/query` —— 团队组件访问量排序（rainbond `urls.py:331 TeamSortServiceQueryView`），调 `queryServiceAccess`

4 个 endpoint 全部走 advice 自动包装为 general_message 形状；权限统一 `@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)`。

### 不在本 change 内（明确推迟）

- 组件自定义监控点 CRUD（`POST/GET/PUT/DELETE /service_monitor` 4 endpoint） → `migrate-console-component-service-monitor`（独立子 change，依赖本 change 落地的 `ServiceMonitor` entity）
- 资源中心 Pod 详情 / Workload 详情 / 容器组日志 → `migrate-console-resource-center`（P0 子 change）
- enterprise 监控聚合（`/openapi/v1/enterprise/{eid}/monitor/{performance,resource_overview,service_overview,component_memory_overview}` 4 个 OpenAPI v1 端点的真实数据填充） → `migrate-openapi-monitor-aggregate`（独立 hardening change，本 change 落地的 `getMonitorMetrics` 是其 foundation）
- 集群级 events（不带 tenant / namespace 过滤） → 已在 `migrate-console-cluster-extras` 落地为 `ClusterOperations.getClusterEvents`，本 change 不重复
- PromQL 自动注入 service_id label（rainbond `promql_service.add_or_update_label`）→ 沿用既有 `query` / `queryRange` 调用方在 controller 层注入的逻辑，不在 region 调用层处理

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "组件监控指标透传与本地监控点 entity 落地"。覆盖 `MonitorOperations` 接口 4 method 调整 / 新增的 region URL 路径与响应透传约束、4 个新 controller endpoint 契约、`ServiceMonitor` entity 字段与 rainbond schema 的一致性约束、与 `migrate-console-cluster-extras` / `migrate-console-resource-center` / `migrate-openapi-monitor-aggregate` 子 change 的解耦边界。

## Impact

- **代码新增**：
  - controller endpoint：扩 `AppMonitorController` +4 endpoint（不新建 controller 文件）
  - region API：扩 `MonitorOperations` 接口 +2 method 签名（默认 default unsupported 占位）+ 调整 2 method 命名拆分；`MonitorOperationsImpl @Primary` 实现 +4 method override（含拆分的 access query 落地）
  - entity：新增 `ServiceMonitor`（`tenant_service_monitor` 表）+ `ServiceMonitorRepository`
  - 单测：`MonitorOperationsImplExtraTest`（新增 4 method 各 1 happy + 1 region 5xx 透传 / `ServiceMonitor` entity 字段映射断言）+ controller 4 endpoint 集成测试
- **数据库**：无 DDL 变更。`tenant_service_monitor` 表 schema 真相由 rainbond Django migrations 拥有；JPA `ddl-auto=validate` 模式下 entity 字段必须与既有表完全一致，启动失败说明字段映射错误
- **依赖**：不引入新 maven 依赖
- **跨 change 衔接**：
  - `migrate-console-component-service-monitor`（计划内子 change）后续在本 change 落地的 `ServiceMonitor` entity 上扩 controller / region 调用（不修改 entity 字段）
  - `migrate-openapi-monitor-aggregate`（计划内子 change）调用 `MonitorOperations.getMonitorMetrics` 拼 enterprise 维度聚合，不在 OpenAPI v1 controller 内重复 region URL 拼装
- **不影响**：rainbond-console（仍可独立跑 7070）、其他已迁移 change（既有 4 method `query` / `queryRange` / `batchQuery` / `getServiceResources` 接口签名不变）
- **路径变量**：路径中 `{team_name}` / `{service_alias}` / `{region_name}` 全部 snake_case（与项目硬约束一致）；resource-center events query string 含 `kind` / `namespace` / `service` / `name` 等可选过滤项，按 rainbond Python `params` 透传 region
