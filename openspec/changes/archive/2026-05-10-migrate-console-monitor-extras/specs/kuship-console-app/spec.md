## ADDED Requirements

### Requirement: 组件监控指标透传与本地监控点 entity 落地

kuship-console SHALL 在既有 `MonitorOperations` 接口（已实现 4 个 method：`query` / `queryRange` / `batchQuery` / `getServiceResources`）基础上扩展 4 个 method（`getMonitorMetrics` / `getResourceCenterEvents` / `queryDomainAccess` / `queryServiceAccess`），全部对 region API 做 1:1 透传实现，并提供 4 个新 console controller endpoint（挂在既有 `AppMonitorController` 上扩展）暴露给 UI。本 Requirement 同时锁定与 `migrate-console-cluster-extras`（集群级 events，enterprise 视角）/ `migrate-console-resource-center`（资源中心 Pod / Workload 详情）/ `migrate-console-component-service-monitor`（组件自定义监控点 CRUD）/ `migrate-openapi-monitor-aggregate`（OpenAPI v1 监控聚合）4 个子 change 的解耦边界。本 change 还落地新的本地 entity `ServiceMonitor`，映射 rainbond `tenant_service_monitor` 表（rainbond `console/models/main.py:1086`），作为后续组件自定义监控点 controller 的 foundation。

业务规则：

- `MonitorOperations.getMonitorMetrics(regionName, tenantId, target, appId, componentId)` MUST 调 region URL `/v2/monitor/metrics?target=&tenant=&app=&component=`，4 个 query param 全部 URL encode；`appId` / `componentId` 为空时仍按空字符串透传（与 rainbond Python `get_monitor_metrics` 默认参数行为一致）；`tenantId` 为 32-char UUID（不是 `tenant_name`），与 rainbond `tenant.tenant_id` 用法一致
- `MonitorOperations.getResourceCenterEvents(regionName, tenantName, queryParams)` MUST 调 region URL `/v2/tenants/{tenant_name}/resource-center/events?<query string>`，`tenant_name` 路径段直传 `team_name`（**不**做 `Tenants.namespace` 替换，与 `cluster-extras.getResources` 不同）；`queryParams` 序列化为 URL query string（TreeMap 字典序排序，URL encode key+value，空 value 跳过）
- `MonitorOperations.queryDomainAccess` 与 `queryServiceAccess` MUST 调 region URL `/api/v1/query?<query string>`（**不带** `/v2/tenants/{tenant_name}` 前缀，与 rainbond Python `get_query_domain_access` / `get_query_service_access` 锚点一致）；接口层独立成两个 method 是为业务语义独立（团队页"访问量排序"卡片专用），不与通用 `query` 方法混淆
- 4 个新增 / 调整 method 与既有 4 method（`query` / `queryRange` / `batchQuery` / `getServiceResources`）共存于同一 `MonitorOperations` 接口上，既有 4 method 的接口签名与实现 SHALL NOT 在本 change 内修改
- `ServiceMonitor` entity MUST 与 rainbond `tenant_service_monitor` 表 schema 完全一致（8 列 + PK）：`ID` / `name` / `tenant_id` / `service_id` / `path` / `port` / `service_show_name` / `interval` / `create_time`；JPA `ddl-auto=validate` 模式下启动失败说明字段映射错误
- `ServiceMonitor` entity SHALL NOT 加 `@Version` 列（与项目硬约束一致 —— Django 端不认识此字段会破坏写入），`id` MUST 用 `Integer`（rainbond Django INT 4 字节）
- `ServiceMonitor` 的 unique 约束 `(name, tenant_id)` 由数据库强制 + repository 层 `existsByTenantIdAndName` 快速校验（用于"创建监控点"前置）
- 4 个新 endpoint 全部走默认 JWT 鉴权链，权限统一 `@RequirePerm(APP_OVERVIEW_DESCRIBE)`，不进 permitAll
- region 异常 MUST 透传 `RegionApiException`，由 `GlobalExceptionHandler` 自动映射为 general_message（**不沿用** rainbond Python `AppMonitorQueryView.get` 的 `try/except: result = ...bean=[]` 异常吞掉降级策略）
- `getResourceCenterEvents` 与 `migrate-console-cluster-extras` 落地的 `ClusterOperations.getClusterEvents` SHALL 维持双 method 并存：前者 tenant 维度（`/v2/tenants/{tn}/resource-center/events`）+ 团队权限，后者 cluster 维度（`/v2/cluster/events`）+ enterprise admin 权限，UI 不同页面分别调用，互不替代

#### Scenario: 组件指标查询 happy path

- **GIVEN** team `default` 下组件 `my-app` 的 `service_id="abc123"`，`tenant_id="t001"`，`region_name="rainbond"`
- **WHEN** `GET /console/teams/default/apps/my-app/metrics`
- **THEN** kuship 调 region `GET /v2/monitor/metrics?target=component&tenant=t001&app=&component=abc123`
- **AND** 响应 200 + general_message 形状 + `data.list` 含若干 metric bean（透传 region）

#### Scenario: 资源中心事件 query 透传

- **GIVEN** team `default`，region `rainbond`
- **WHEN** `GET /console/teams/default/regions/rainbond/resource-center/events?kind=Pod&namespace=ns1&service=svc1&name=pod-x`
- **THEN** kuship 调 region `GET /v2/tenants/default/resource-center/events?kind=Pod&name=pod-x&namespace=ns1&service=svc1`（参数字典序排序，全部 URL encode）
- **AND** 响应原样透传 region 的 events bean / list

#### Scenario: 团队域名访问量排序

- **WHEN** `GET /console/teams/default/region/rainbond/sort_domain/query?repo=1&page=1&page_size=5`
- **THEN** kuship 用 `tenant.tenant_id` 拼 PromQL `sort_desc(sum(ceil(increase(gateway_requests{namespace="<tid>"}[1h]))) by (host))` 调 region `GET /api/v1/query?<encoded promql>`
- **AND** 响应 200 + `data.bean.total` + `data.bean.total_traffic` + `data.list` 分页正确（`(page-1)*page_size : page*page_size`）

#### Scenario: 团队组件访问量排序

- **WHEN** `GET /console/teams/default/region/rainbond/sort_service/query`
- **THEN** kuship 调 region 两次 `GET /api/v1/query?<promql>`：
  - 一次 outer：`sort_desc(sum(ceil(increase(gateway_requests{namespace="<tid>"}[1h]))) by (service))`
  - 一次 inner：`sort_desc(sum(ceil(increase(app_request{tenant_id="<tid>",method="total"}[1h]))) by (service_id))`
- **AND** 响应 200 + `data.list` 含合并去重后的 outer + inner top 10 service

#### Scenario: ServiceMonitor entity 字段一致性

- **GIVEN** rainbond `tenant_service_monitor` 表已存在（rainbond Django migration 拥有 schema 真相）
- **WHEN** kuship-console 启动 application 进入 hibernate `ddl-auto=validate` 阶段
- **THEN** `ServiceMonitor` entity 的 8 列字段（`ID` / `name` / `tenant_id` / `service_id` / `path` / `port` / `service_show_name` / `interval` / `create_time`）与表 schema 1:1 对齐，校验通过
- **AND** application 启动成功
- **AND** `ServiceMonitorRepository.findByTenantIdAndServiceId(tid, sid)` 返回空 list（组件无监控点时）或 ServiceMonitor 列表（有监控点时）

#### Scenario: ServiceMonitor unique 约束

- **GIVEN** 已存在一条 `ServiceMonitor{name="cpu_metric", tenantId="t001", ...}`
- **WHEN** 业务层调 `serviceMonitorRepository.save(new ServiceMonitor("cpu_metric", "t001", ...))` 重复 INSERT
- **THEN** 抛 `DataIntegrityViolationException`（数据库 unique key `(name, tenant_id)` 强制）

#### Scenario: region 异常透传不降级

- **GIVEN** region 端在 `/v2/monitor/metrics?...` 返 503
- **WHEN** `GET /console/teams/default/apps/my-app/metrics`
- **THEN** kuship **不**沿用 rainbond Python 的 `try/except: bean=[]` 降级，**不**返 200 + 空 list
- **AND** 响应 503 + general_message 形状（`msg` / `msg_show` 来自 region），HTTP status code 等于 region code

#### Scenario: 与 cluster-extras `getClusterEvents` 的边界

- **GIVEN** 本 change 已落地 `MonitorOperations.getResourceCenterEvents`，`migrate-console-cluster-extras` 已落地 `ClusterOperations.getClusterEvents`
- **WHEN** 平台管理员在集群资源页查看全量集群事件
- **THEN** UI SHALL 调 `/console/enterprise/{eid}/regions/{rn}/cluster-events`（cluster-extras 端点），不调 monitor-extras 端点
- **AND** 团队成员在应用资源中心页查看本团队 Pod 事件 SHALL 调 `/console/teams/{team_name}/regions/{region_name}/resource-center/events`（本 change 端点），不调 cluster-extras 端点
- **AND** 两 method 共存于不同 Operations 接口（`ClusterOperations` vs `MonitorOperations`），互不替代
