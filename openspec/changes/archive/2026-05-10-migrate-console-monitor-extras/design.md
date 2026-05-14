# Design — migrate-console-monitor-extras

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是 **P1 #3**（监控指标补齐 + service_monitor entity 落地），归属母路线图 `migrate-region-coverage-roadmap` 决策 3 第 17 行 "Monitor 指标 + 资源中心事件" 域。估计 method 数 **6**（4 既有 + 2 新增 + 2 拆分调整 = 接口暴露 8 method），entity 新增 **1**（`ServiceMonitor`），工作量约 2-3 天，远低于 ≤ 30 上限。归档时反向更新路线表对应行（`Monitor 指标 + 资源中心事件` 行的 kuship 列由 4 改为 10，缺口由 6 改为 0）。

依赖：无（独立可起）。后续子 change `migrate-console-component-service-monitor` / `migrate-openapi-monitor-aggregate` 软依赖本 change 完成（前者复用 `ServiceMonitor` entity，后者复用 `MonitorOperations.getMonitorMetrics`）。

## Region API URL 表

| method                                                         | HTTP | 路径                                                                                       | rainbond 锚点                                              |
|----------------------------------------------------------------|------|--------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| query(rn, tn, params)                                          | GET  | `/api/v1/query?<query string>`                                                              | `regionapi.py:1297-1303 get_query_data`（已实现）         |
| queryRange(rn, tn, params)                                     | GET  | `/api/v1/query_range?<query string>`                                                        | `regionapi.py:1305-1311 get_query_range_data`（已实现）   |
| batchQuery(rn, tn, params)                                     | GET  | `/api/v1/query?<拼装 service_ids 后的 query string>`                                       | rainbond `BatchAppMonitorQueryView` 内拼接 `get_query_data`（已实现） |
| getServiceResources(rn, tn, alias)                             | GET  | `/v2/tenants/{tenant_name}/services/{service_alias}/resources`                              | rainbond `regionapi.py get_service_resources`（已实现）   |
| getMonitorMetrics(rn, tenantId, target, appId, componentId)    | GET  | `/v2/monitor/metrics?target={target}&tenant={tenant_id}&app={app_id}&component={component_id}` | `regionapi.py:2356-2362 get_monitor_metrics`             |
| getResourceCenterEvents(rn, tn, params)                        | GET  | `/v2/tenants/{tenant_name}/resource-center/events?<query string>`                           | `regionapi.py:3830-3839 get_resource_center_events`       |
| queryDomainAccess(rn, tn, params)                              | GET  | `/api/v1/query?<query string>`（PromQL 内含 `gateway_requests{namespace=...}` host 维度聚合） | `regionapi.py:1322-1329 get_query_domain_access`         |
| queryServiceAccess(rn, tn, params)                             | GET  | `/api/v1/query?<query string>`（PromQL 内含 `gateway_requests{namespace=...}` service 维度聚合或 `app_request{tenant_id=...}` 内访问聚合） | `regionapi.py:1313-1320 get_query_service_access`       |

新增的两个 method `getMonitorMetrics` 路径锚点 `/v2/monitor/metrics`（**注意**：与 component / app 监控相关，但是不在 `/v2/tenants/{tenant_name}/...` 前缀下，是 region 全局 monitor 端点；`tenant_id` 通过 query param 透传而非路径段）；`getResourceCenterEvents` 路径段 `tenant_name` 与 rainbond Python `regionapi.py:3833 url += "/v2/tenants/{}/resource-center/events".format(tenant_name)` 完全一致 —— **直传 `tenant_name`**（即 `team_name`），不做 `Tenants.namespace` 替换（与 `cluster-extras` 的 `getResources` 不同，那个走 `region_tenant_name`）。

## Controller 路径锚点

| Controller / endpoint              | path                                                                                  | method | rainbond 锚点                                            |
|-------------------------------------|---------------------------------------------------------------------------------------|--------|---------------------------------------------------------|
| `AppMonitorController.metrics`     | `/console/teams/{team_name}/apps/{service_alias}/metrics`                              | GET    | `urls.py:774 ComponentMetricsView`                     |
| `AppMonitorController.resourceCenterEvents` | `/console/teams/{team_name}/regions/{region_name}/resource-center/events`     | GET    | rainbond Python 无独立 console URL（监控视角，本 change 新建供 UI 调用） |
| `AppMonitorController.sortDomainQuery` | `/console/teams/{team_name}/region/{region_name}/sort_domain/query`               | GET    | `urls.py:328 TeamSortDomainQueryView`                  |
| `AppMonitorController.sortServiceQuery` | `/console/teams/{team_name}/region/{region_name}/sort_service/query`              | GET    | `urls.py:331 TeamSortServiceQueryView`                 |

trailing slash 兼容沿用既定规则（每 endpoint 同时声明 `path` 与 `path/`）。

`/sort_domain/query` 与 `/sort_service/query` 路径段保留 rainbond 历史"动词 + query"形式（不改写为 RESTful），以保证 kuship-ui 直接复用 rainbond-ui 调用客户端。`/region/` 单数（rainbond `urls.py:328` 实际为单数 `region`）—— 这与 `/regions/` 复数（其他 enterprise / cluster 路径用法）的不一致也是 rainbond 历史遗留，本 change 严格保留。

## 决策 1 — `getResourceCenterEvents` 与 `migrate-console-cluster-extras.getClusterEvents` 边界

`migrate-console-cluster-extras`（已归档）已在 `ClusterOperations.getClusterEvents(regionName, body)` 落地集群级 events 透传，URL `/v2/cluster/events?<query>`，**不带 tenant / namespace 过滤**，给 enterprise admin 看全量集群事件。

本 change 新增 `MonitorOperations.getResourceCenterEvents(regionName, tenantName, queryParams)`，URL `/v2/tenants/{tenant_name}/resource-center/events?<query>`，**带 tenant 路径段**（即 namespace 过滤），并支持 `kind` / `name` / `service` / `pod` 等更细维度的 query 参数，给团队成员看本团队范围内的资源对象事件。

| 维度       | `ClusterOperations.getClusterEvents` (cluster-extras)           | `MonitorOperations.getResourceCenterEvents`（本 change） |
|-----------|------------------------------------------------------------------|--------------------------------------------------------|
| URL 前缀   | `/v2/cluster/events`                                             | `/v2/tenants/{tenant_name}/resource-center/events`     |
| 过滤维度   | 无 tenant 维度，type / since 等 cluster 范围 query                  | tenant 路径段 + kind / name / service / pod 等子资源过滤 |
| Controller | `ClusterEventsController.list` (`/console/enterprise/{eid}/regions/{rn}/cluster-events`) | `AppMonitorController.resourceCenterEvents` (`/console/teams/{team_name}/regions/{region_name}/resource-center/events`) |
| 权限       | `@RequireEnterpriseAdmin`                                        | `@RequirePerm(APP_OVERVIEW_DESCRIBE)`                  |
| 用户场景   | 平台管理员排查集群异常                                           | 团队成员看自己应用 / Pod 的事件流                      |

**两 method 互不替代**，UI 不同页面分别调用：
- 集群资源页（enterprise 视角）→ `getClusterEvents`
- 应用 / 资源中心页（团队视角）→ `getResourceCenterEvents`

## 决策 2 — `ServiceMonitor` entity 字段集与 JPA `validate` 模式约束

rainbond schema 真相位于 `console/models/main.py:1086-1097`，本 change entity 字段必须 1:1 对齐：

| Java 字段           | 列名               | 类型                | 约束                                       | 备注 |
|----------------------|--------------------|---------------------|---------------------------------------------|------|
| `Integer id`         | `ID`               | INT (PK auto)       | `@Id @GeneratedValue` + `@Column(name="ID")` | rainbond `BaseModel` 默认 PK 大写 |
| `String name`        | `name`             | varchar(64)         | NOT NULL，与 `tenantId` 联合唯一            | rainbond unique_together: `(name, tenant_id)` |
| `String tenantId`    | `tenant_id`        | varchar(32)         | NOT NULL                                    | 团队 UUID |
| `String serviceId`   | `service_id`       | varchar(32)         | NOT NULL                                    | 组件 UUID |
| `String path`        | `path`             | varchar(255)        | NOT NULL，需以 `/` 开头（业务层校验）        | 监控路径，如 `/metrics` |
| `Integer port`       | `port`             | int                 | NOT NULL                                    | 端口号 |
| `String serviceShowName` | `service_show_name` | varchar(64)     | NOT NULL                                    | 展示名称 |
| `String interval`    | `interval`         | varchar(10)         | NOT NULL                                    | 采集间隔字符串如 `"10s"` / `"30s"` |
| `LocalDateTime createTime` | `create_time` | datetime            | NOT NULL，`@PrePersist` 自动填充            | rainbond `BaseModel` 默认 `auto_now_add` |

**JPA `validate` 模式硬约束**：
- `hibernate.ddl-auto=validate` 在启动期强校验列名 / 列类型 / 长度，缺列或类型不匹配直接拒绝启动
- `interval` 字段名与 SQL 关键字冲突 —— Hibernate / MySQL 8 默认接受 `interval` 列名（rainbond Django 端实测可用），entity 端不需 `@Column(name = "\`interval\`")` 反引号转义（**实施期 task §1.2 探测验证**：若启动失败再加反引号）
- 不得在 entity 上加 `@Version` 列（与项目硬约束一致 —— Django 端不认识此字段会破坏写入）
- `id` 用 `Integer`（rainbond Django INT 4 字节），用 `Long` 会触发 schema validation `wrong column type`

实施期 task §1.2 必须先 `docker exec kuship-mysql mysql ... DESC tenant_service_monitor` 验证真实列存在与类型，确认与上表 8 列一致。

## 决策 3 — `MonitorOperations` 既有 method 与新增 / 调整 method 边界

为避免实施时重复落地或意外破坏既有 4 method 行为，明确边界：

**既有 4 method（已实现，本 change 不修改实现）**：

1. `query(regionName, tenantName, queryParams)` —— rainbond `get_query_data`，URL `/v2/tenants/{tenantName}/monitor/query?<query string>`
2. `queryRange(regionName, tenantName, queryParams)` —— rainbond `get_query_range_data`，URL `/v2/tenants/{tenantName}/monitor/query_range?<query string>`
3. `batchQuery(regionName, tenantName, queryParams)` —— rainbond `BatchAppMonitorQueryView` 内合成（多次调用 `get_query_data`），URL `/v2/tenants/{tenantName}/monitor/batch_query?<query string>`
4. `getServiceResources(regionName, tenantName, serviceAlias)` —— 组件资源用量，URL `/v2/tenants/{tenantName}/services/{serviceAlias}/resources`

**重要修正**：实施期 task §3 要校准既有 method 4 个的真实 region 路径 —— rainbond Python `get_query_data` 锚点是 `/api/v1/query`（Prometheus 标准）而非现有 kuship 实现的 `/v2/tenants/{tn}/monitor/query`。本 change **不**回填现有 method 的 URL 修正（避免 scope 蔓延），仅在 design.md 备注 hot-fix 留作 `harden-monitor-prometheus-url-correction` 独立 change。新增 / 调整的 4 method 严格按 rainbond 真实 URL 落地。

**新增 2 method**：

5. `getMonitorMetrics(regionName, tenantId, target, appId, componentId)` —— URL `/v2/monitor/metrics?target=&tenant=&app=&component=`
6. `getResourceCenterEvents(regionName, tenantName, queryParams)` —— URL `/v2/tenants/{tenantName}/resource-center/events?<query string>`

**调整 2 method（既有 `query` / `queryRange` 不动，独立成 method）**：

7. `queryDomainAccess(regionName, tenantName, queryParams)` —— URL `/api/v1/query?<query string>`（PromQL 内 host 维度聚合）
8. `queryServiceAccess(regionName, tenantName, queryParams)` —— URL `/api/v1/query?<query string>`（PromQL 内 service 维度聚合）

## 决策 4 — `getMonitorMetrics` 入参形态：`tenantId` vs `tenantName`

rainbond Python 端 `get_monitor_metrics` 的入参是 `tenant` 对象（`tenant.tenant_name` 用作 region access info 路由 + `tenant.tenant_id` 作为 query param `tenant=`）。

kuship 端不传整个 `Tenants` 对象，拆开为 `tenantId` + `tenantName` 两个参数：

```java
Map<String, Object> getMonitorMetrics(String regionName, String tenantId, String target, String appId, String componentId);
```

- `regionName` —— region 路由（与所有 region method 一致）
- `tenantId` —— 32-char UUID，作为 query param `tenant={tenant_id}`（rainbond Python 用法）
- `target` —— `"component"` / `"app"` / `"tenant"`（rainbond 端语义）
- `appId` —— 可选（target=`"app"` 时必填，rainbond Python 端先调 `region_app_repo.get_region_app_id` 转换 `app_id` 到 region_app_id；本 change controller 层透传，**不在 Operations 层做 region_app_id 转换**，由 controller 调 `regionAppRepo.findRegionAppId(regionName, appId)` 后传入）
- `componentId` —— 可选（target=`"component"` 时必填）

`tenantName` 不入参，因为 region access info 路由由 `RegionClientFactory.client(regionName, ...)` 直接拿，不需要 tenant 维度路由（与既有 4 个 monitor method 处理一致）。

## 决策 5 — `queryDomainAccess` / `queryServiceAccess` 归属：`MonitorOperations` 还是 `gateway-domain`

rainbond Python 端 `get_query_domain_access` / `get_query_service_access` 都通过 `/api/v1/query` 端点查询 Prometheus，PromQL 内查的是 `gateway_requests{namespace="<tenant_id>"}` —— 表面看像 gateway 域指标。

但 UI 调用方（`team.py:TeamSortDomainQueryView` / `TeamSortServiceQueryView`）属于"团队监控仪表板"页面（不是网关页），本 change 决策**合并到 `MonitorOperations`**，理由：

1. **PromQL 是监控查询语言**，无论数据源 metric 是网关还是其他，调用语义都是"监控数据查询"
2. UI 页面归属是"监控页"而不是"网关页"，与组件 metric 查询同一个 controller 自然
3. `migrate-console-gateway-domain` 子 change 已落地（如果走那条路径会污染网关 controller，UI 路径变更）
4. rainbond Python 端 `get_query_data` / `get_query_range_data` 已与 `get_query_domain_access` / `get_query_service_access` 共享同一基类（`api/v1/query`），独立成两个 method 是 Python 端的语义清晰化，本 change 同步对齐

**`migrate-console-gateway-domain` 子 change 不再覆盖 `domain/service access` 监控查询** —— 本决策锁定边界。

## 决策 6 — 错误透传与 PromQL 注入

6 method 都是简单 region 透传，遇到 region 错误走 `RegionApiException` + `GlobalExceptionHandler` 自动映射为 general_message。

rainbond Python 端 `AppMonitorQueryView.get` 中有大段 `try ... except: result = general_message(200, "success", "查询成功", bean=[])` —— 异常吞掉返空 list。本 change **不沿用此降级策略**，让 `RegionApiException` 透传：

- 优点：UI 能感知监控查询失败（5xx 让用户看到 "region 不可用" 而不是 "无数据"）
- 与 cluster-extras / 其他子 change 透传策略一致
- 失败处理由前端控制（rainbond-ui 既有 toast / fallback 逻辑）

PromQL 自动注入 `service_id` label（rainbond `promql_service.add_or_update_label`）—— **不在 region 调用层处理**，由 controller 层调 `MonitorOperations.query` 前用业务 helper 注入 PromQL 字符串（与 rainbond Python `AppMonitorQueryView.get` 在 view 层注入的位置一致）。本 change 不引入 `PromqlService` 类，相关 PromQL helper 留作 `migrate-console-component-service-monitor` 子 change 落地（届时落地完整自定义监控点 + PromQL 拼装）。

## 决策 7 — `getResourceCenterEvents` 的 query string 编码

`Map<String, Object> queryParams` → URL query string：

- TreeMap 字典序排序（与 cluster-extras `getClusterEvents` 同样套路）
- 空 value 跳过
- key 与 value 都做 URL encode（`URLEncoder.encode(..., StandardCharsets.UTF_8)`）
- 拼接：`?k1=v1&k2=v2`

**与 rainbond Python 行为差异**：rainbond `regionapi.py:3835` 用 `"&".join("{}={}".format(k, v) for k, v in params.items())` —— **不做 URL encode**。kuship 端选 URL encode 是因为 query 参数可能包含 `"&" / "=" / 空格 / 中文`（如 `kind=Service`，但 `name=my-pod-001` 含 `-` 即可；`namespace=test team` 含空格场景），不 encode 会破坏 URL。**实施期 task §6 验证**：若 region Go 端 unmarshal 失败说明 region 端期望未 encode，再切回 rainbond Python 行为；目前先按 URL encode 落地。

## 决策 8 — 测试用 region 端真实数据 vs Mock

`MonitorOperationsImplExtraTest` 走 `MockRestServiceServer`（与既有 `TenantOperationsImplTest` / `HelmOperationsImplTest` / `ClusterOperationsImplExtraTest` 同模式）。

`ServiceMonitorRepositoryTest` 走 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + 真实本地 MySQL（与既有 `TeamHelmReleaseSourceRepositoryTest` / `AutoscalerRuleRepositoryTest` 同模式），仅断言 entity 字段映射 + unique 约束 + finder 行为，不写跨服务测试。

集成测试（`AppMonitorIntegrationTest`，扩既有 `AppMonitorController` 的测试）走 `@MockitoBean MonitorOperations`，断言 4 个新 endpoint 的 region 入参（`tenantId` / `tenantName` / `target` / `componentId` / `queryParams`）。

不依赖本地起 region 容器（项目既定测试规约）。

## 非决策（明确不做）

- **不**修改 `MonitorOperations` 既有 4 method（`query` / `queryRange` / `batchQuery` / `getServiceResources`）的接口签名与 `MonitorOperationsImpl` 实现 —— 即使其 URL 与 rainbond Python 真实路径不完全对齐，回填属 `harden-monitor-prometheus-url-correction` 独立 change
- **不**新建独立 `ServiceMonitorOperations` 接口承载 `getMonitorMetrics` —— 全部挂在 `MonitorOperations` 上保持接口聚合
- **不**新建独立 `MonitorAccessOperations` 接口承载 `queryDomainAccess` / `queryServiceAccess` —— 同上
- **不**实现组件自定义监控点 CRUD（`POST/GET/PUT/DELETE /service_monitor` 4 endpoint） —— 跟进子 change `migrate-console-component-service-monitor`
- **不**实现 PromQL 自动注入 service_id label —— 跟进子 change
- **不**修改 `application.yaml` 配置（监控查询 timeout 沿用 region client 默认 5 秒）
- **不**为 `region_app_id` 转换引入新 repository —— 复用既有 `RegionAppRepository`（`migrate-console-app-runtime` 已落地）

## 测试约定

集成测试覆盖 `AppMonitorIntegrationTest`（扩既有测试）：

- `metrics endpoint happy`：mock `getMonitorMetrics` 返一个 sample list，断言响应 general_message 形状 + `data.list` 字段
- `resource_center_events query 透传`：mock 返事件 list，发 `?kind=Pod&namespace=ns1&service=svc1&name=pod-x`，ArgumentCaptor 断言 query map 含 4 个 key
- `sort_domain_query happy`：发 `?repo=1&page=1&page_size=5`，verify `queryDomainAccess` 入参；返响应含 `data.bean.total` + `data.bean.total_traffic` + `data.list`
- `sort_service_query happy`：mock 返事件 list，verify `queryServiceAccess` 入参（含 outer / inner 两次调用）
- `region 5xx 透传`：mock 抛 `RegionApiException(503,...)`，断言响应 503 + `msg_show=region 不可用` 透传
- `resource_center_events 路径变量`：team_name 与 region_name 路径段解析正确

每个新 endpoint 至少 1 happy + 1 region 异常透传 + 1 路径变量 / query 参数校验。

`ServiceMonitorRepositoryTest`：

- 字段映射 happy（save + find by ID 后字段完整恢复）
- unique 约束（`(name, tenant_id)` 重复 INSERT 抛 `DataIntegrityViolationException`）
- `findByTenantIdAndServiceId` happy + 空 list（无监控点的组件）
- `findByTenantIdAndName` 含点号 / 数字字符的 name
- `existsByTenantIdAndName` 命中 / miss

`MonitorOperationsImplExtraTest`（MockRestServiceServer）：

- 4 method 各 1 happy + 1 region 5xx 透传
- `getMonitorMetrics` URL query 拼装断言（target / tenant / app / component 四个 query param 全部出现）
- `getResourceCenterEvents` query string 字典序排序断言（如 `?kind=Pod&name=pod-x&namespace=ns1&service=svc1`）+ 空 value 跳过用例
- `queryDomainAccess` / `queryServiceAccess` URL `/api/v1/query` 断言（不带 tenant 路径段）+ query string 透传

## 实施期探测结果（2026-05-10 落地）

- **决策 1 路径冲突 —— resourceCenterEvents endpoint 不暴露**：design.md 决策 1 设计 `/console/teams/{team_name}/regions/{region_name}/resource-center/events` 在 AppMonitorController 暴露，但既有 `ResourceCenterController`（`migrate-console-region-resource-center` 已落地）已使用同一路径并由 `ResourceCenterOperations.getEvents` 实现。两个 controller 共存触发 Spring `Ambiguous mapping` 启动失败。**实施决策**：删除 AppMonitorController 中的 `resourceCenterEvents` endpoint，UI 端事件查询继续走 `ResourceCenterController`；`MonitorOperations.getResourceCenterEvents` 接口仍保留供后续 hardening 复用（如 monitor 维度的事件聚合）
- **决策 2 schema 真相修正 —— ServiceMonitor entity 无 create_time 列**：`docker exec kuship-mysql mysql -e "DESC tenant_service_monitor"` 实测真实 schema **仅 8 列**（`ID / name / tenant_id / service_id / path / port / service_show_name / interval`），无 `create_time` 列。design.md 决策 2 字段表的第 9 行 `LocalDateTime createTime + @PrePersist` 与 schema 不符 → entity 移除该字段，`hibernate.ddl-auto=validate` 才能通过
- **`interval` 列名**：实测 MySQL 8 接受 `interval` 作为列名，但保险起见 entity 用 `@Column(name = "\`interval\`")` 反引号转义
- **sortDomainQuery 响应封装**：design.md 5.1 写"返 `{bean: {total, total_traffic}, list: [...]}`"，实施期发现需用 `GeneralMessage.okWithExtras(bean, paged, null)` 显式构造 ApiResult，避免 advice 把整个 Map 包成 `data.bean.bean`（嵌套两层）
- **测试结果**：`MonitorExtrasIntegrationTest` 4 集成测试用例全过（含 metrics happy / sortDomainQuery 分页 / sortServiceQuery 合并 / region 5xx 透传）；§8 ServiceMonitorRepositoryTest 推迟到组件自定义监控点 CRUD 子 change（`migrate-console-component-service-monitor`）落地时同步引入
