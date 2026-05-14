# Tasks — migrate-console-monitor-extras

## 1. 校验既有接口与 schema 真相

- [ ] 1.1 确认 `cn.kuship.console.modules.appruntime.api.MonitorOperations` 仍持有既有 4 method（`query` / `queryRange` / `batchQuery` / `getServiceResources`）；如签名已被其他 change 修改，先与作者对齐
- [ ] 1.2 用 `docker exec kuship-mysql mysql ... DESC tenant_service_monitor` 验证表 schema 真相：8 列存在与类型与 design.md 决策 2 表一致（`ID` int / `name` varchar(64) / `tenant_id` varchar(32) / `service_id` varchar(32) / `path` varchar(255) / `port` int / `service_show_name` varchar(64) / `interval` varchar(10) / `create_time` datetime）
- [ ] 1.3 验证 `interval` 列名与 SQL 关键字冲突 —— 启动 application 后观察 hibernate `validate` 模式是否报错，需要时 entity 用 `@Column(name = "\`interval\`")` 反引号转义
- [ ] 1.4 探测 region 端：用本地 console 已注入的 RegionClient，curl 真实节点验证 4 个新增 / 调整 URL 路径返 200：
  - `GET /v2/monitor/metrics?target=component&tenant=<tenant_id>&app=&component=<service_id>` → 期望 200 + `data.list`
  - `GET /v2/tenants/<tenant_name>/resource-center/events?kind=Pod` → 期望 200 + bean
  - `GET /api/v1/query?query=<promql>`（domain access）→ 期望 200 + `data.result`
  - `GET /api/v1/query?query=<promql>`（service access）→ 期望 200 + `data.result`
- [ ] 1.5 把 1.4 的探测结果写进 `design.md` 决策 7（query string 编码）下方"探测结果"小节，明确最终 region 端是否要 URL encode 与字典序排序

## 2. 新增 `ServiceMonitor` entity 与 repository

- [ ] 2.1 新建 `cn.kuship.console.modules.appruntime.entity.ServiceMonitor`：
  - `@Entity @Table(name = "tenant_service_monitor")`
  - 字段按 design.md 决策 2 表 8 列 + `id` PK 9 字段，类型与列名 1:1 对齐
  - `id` 用 `Integer` `@Id @GeneratedValue(strategy = IDENTITY) @Column(name = "ID")`
  - `interval` 字段名按 task §1.3 探测结果决定是否反引号转义
  - `createTime` 用 `LocalDateTime` + `@PrePersist` 回调自动填充（rainbond `BaseModel.create_time` 默认 `auto_now_add` 行为）
  - **不**加 `@Version` 列（与项目硬约束一致）
- [ ] 2.2 新建 `cn.kuship.console.modules.appruntime.repository.ServiceMonitorRepository extends JpaRepository<ServiceMonitor, Integer>`：
  - `List<ServiceMonitor> findByTenantIdAndServiceId(String tenantId, String serviceId)` —— 列出组件下所有监控点
  - `Optional<ServiceMonitor> findByTenantIdAndName(String tenantId, String name)` —— unique 校验 / 单点详情
  - `boolean existsByTenantIdAndName(String tenantId, String name)` —— 创建前重名快速校验
- [ ] 2.3 启动 `kuship-console`（dev profile）验证 hibernate `validate` 模式不报错；如报错按 §1.2 schema 真相修正字段映射

## 3. 扩展 `MonitorOperations` 接口（+2 method 新增 + 2 method 调整）

- [ ] 3.1 在 `MonitorOperations.java` 接口加 4 个 method 签名（既有 4 method 保留，签名不动）：
  ```java
  Map<String, Object> getMonitorMetrics(String regionName, String tenantId, String target, String appId, String componentId);
  Map<String, Object> getResourceCenterEvents(String regionName, String tenantName, Map<String, String> queryParams);
  Map<String, Object> queryDomainAccess(String regionName, String tenantName, Map<String, String> queryParams);
  Map<String, Object> queryServiceAccess(String regionName, String tenantName, Map<String, String> queryParams);
  ```
- [ ] 3.2 4 method 在接口上声明为 `default` 占位（抛 `UnsupportedOperationException("not yet implemented; will be filled in by migrate-console-monitor-extras")`），保证未注入 @Primary impl 时也能编译通过 — 与 14 接口骨架沿用模式一致

## 4. `MonitorOperationsImpl` 实现 4 method

- [ ] 4.1 在既有 `cn.kuship.console.modules.appruntime.api.MonitorOperationsImpl`（已 `@Primary`）追加 4 个 method override —— **不新建文件**
- [ ] 4.2 实现 `getMonitorMetrics(regionName, tenantId, target, appId, componentId)`：
  - URL = `/v2/monitor/metrics?target={target}&tenant={tenantId}&app={appId}&component={componentId}`
  - 4 个 query param 全部 URL encode；`appId` / `componentId` 缺省（null 或空字符串）时仍按空字符串透传（与 rainbond Python `get_monitor_metrics` 默认参数 `app_id=""`/`component_id=""` 行为一致）
  - GET 透传 → `processor.extractBean(resp, Map.class, ...)` 返 Map（含 `list` 字段）
- [ ] 4.3 实现 `getResourceCenterEvents(regionName, tenantName, queryParams)`：
  - URL = `/v2/tenants/{tenantName}/resource-center/events` + (queryString 非空时 `?` + queryString)
  - `Map<String, String> queryParams` → query string：TreeMap 字典序排序遍历，URL encode key+value，按 `&` 拼接；空 value 跳过
  - GET 透传，用 `processor.extractBean(resp, Map.class, ...)` 返 Map
- [ ] 4.4 实现 `queryDomainAccess(regionName, tenantName, queryParams)`：
  - URL = `/api/v1/query` + (queryString 非空时 `?` + queryString)
  - query string 拼装与 §4.3 一致
  - **注意**：URL 不带 `/v2/tenants/{tenantName}` 前缀（rainbond Python `get_query_domain_access` 锚点：`url = url + "/api/v1/query" + params`）
  - GET 透传
- [ ] 4.5 实现 `queryServiceAccess(regionName, tenantName, queryParams)`：与 §4.4 同模板（URL `/api/v1/query`），仅业务语义独立
- [ ] 4.6 单测 `MonitorOperationsImplExtraTest`：8 用例全过
  - 4 method 各 1 happy + 1 region 5xx 透传
  - `getMonitorMetrics` 断言 4 个 query param 拼装正确（`?target=component&tenant=<id>&app=&component=<id>`）
  - `getResourceCenterEvents` 断言 query string 字典序排序（`?kind=Pod&name=pod-x&namespace=ns1&service=svc1`）+ 空 value 跳过用例
  - `queryDomainAccess` / `queryServiceAccess` URL `/api/v1/query` 断言（不带 tenant 路径段）

## 5. 扩展 `AppMonitorController`（+4 endpoint）

- [ ] 5.1 在既有 `cn.kuship.console.modules.appruntime.controller.AppMonitorController` 追加 4 endpoint —— **不新建 controller 文件**：
  - `metrics`：`@GetMapping({"/console/teams/{team_name}/apps/{service_alias}/metrics", ".../metrics/"})`
    - 调 `loader.requireService(teamName, alias)` 拿 `TenantService` + `Tenants`（loader 既有 method）
    - 调 `regionAppRepo.findRegionAppId(regionName, appIdFromQuery)`（**仅 target=app 时调用**；component target 直传 `service.serviceId`）
    - 调 `monitor.getMonitorMetrics(serviceRegion, tenant.tenantId, "component", "", service.serviceId)`
    - 返 Map（advice 自动包装为 `data.list`）
  - `resourceCenterEvents`：`@GetMapping({"/console/teams/{team_name}/regions/{region_name}/resource-center/events", ".../events/"})`
    - `@RequestParam Map<String, String> queryParams` 直接接所有 query 参数
    - 调 `loader.requireTeam(teamName)`
    - 调 `monitor.getResourceCenterEvents(regionName, teamName, new HashMap<>(queryParams))`
  - `sortDomainQuery`：`@GetMapping({"/console/teams/{team_name}/region/{region_name}/sort_domain/query", ".../sort_domain/query/"})`
    - `@RequestParam(required = false) Integer page` / `@RequestParam(required = false) Integer page_size` / `@RequestParam(required = false) String repo`
    - 沿用 rainbond Python `TeamSortDomainQueryView.get` 业务逻辑：拼 PromQL `sort_desc(sum(ceil(increase(gateway_requests{namespace="<tenant_id>"}[1h]))) by (host))` → `queryDomainAccess` 返 `body.data.result` → 客户端分页（`(page-1)*page_size : page*page_size`）+ 累计 `total_traffic`
    - 返 `{bean: {total, total_traffic}, list: [...]}`
  - `sortServiceQuery`：`@GetMapping({"/console/teams/{team_name}/region/{region_name}/sort_service/query", ".../sort_service/query/"})`
    - 沿用 rainbond Python `TeamSortServiceQueryView.get` 业务逻辑：两次调 `queryServiceAccess`（outer `gateway_requests` by service / inner `app_request` by service_id），合并去重后取 top 10
    - 返 `{list: [...]}`
- [ ] 5.2 4 endpoint 全部 `@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)`
- [ ] 5.3 路径变量统一 snake_case，trailing slash 双声明，不显式 ApiResult 包装（advice 自动）

## 6. SecurityConfig 与公开端点

- [ ] 6.1 4 个 endpoint 全部走默认 JWT 鉴权链，**无需** 加 permitAll；本 change 不修改 SecurityConfig

## 7. 集成测试

实施合并到一个 `MonitorExtrasIntegrationTest` 类（@SpringBootTest + @MockitoBean MonitorOperations + @ActiveProfiles({"local","contract-test"})），降低 fixture 重复成本。6+ 用例全过：

- [ ] 7.1 `metrics happy path`：mock `getMonitorMetrics` 返 `{list: [{metric: ...}]}`，响应 200 + `data.list` 含 1 元素 + 五项契约断言
- [ ] 7.2 `resource_center_events query 透传`：发 `?kind=Pod&namespace=ns1&service=svc1&name=pod-x`，ArgumentCaptor 断言 query map 含 4 个 key + 顺序无关
- [ ] 7.3 `sort_domain_query`：
  - happy path（mock `queryDomainAccess` 返 prometheus result，断言响应 `data.bean.total` + `data.bean.total_traffic` + `data.list` 分页正确）
  - region 5xx 透传：mock 抛 `RegionApiException(503,...)`，断言响应 503 + `msg_show` 透传
- [ ] 7.4 `sort_service_query happy`：mock 两次 `queryServiceAccess` 返 outer / inner 不同 service 列表，断言响应 `data.list` 合并去重后元素数
- [ ] 7.5 `resource_center_events 路径变量`：team_name 解析为 namespace 路径段（直传 team_name，不做 namespace 替换） + region_name 路径段透传给 region client
- [ ] 7.6 `metrics 权限 401`：未带 JWT 调 metrics endpoint 返 401（既有 SecurityConfig 行为，仅冒烟）

## 8. 数据库 / Repository 测试

- [ ] 8.1 `ServiceMonitorRepositoryTest`（@DataJpaTest + 真实本地 MySQL）：5 用例全过
  - 字段映射 happy：save 一个 `ServiceMonitor`（含 `interval="10s"` / `path="/metrics"`）+ findById 后字段完整恢复
  - unique 约束：同 `(name, tenant_id)` 重复 INSERT 抛 `DataIntegrityViolationException`
  - `findByTenantIdAndServiceId` happy + 空 list（无监控点的组件）
  - `findByTenantIdAndName` happy（含点号 / 数字 / 中划线 name 字符）
  - `existsByTenantIdAndName` 命中 / miss

## 9. 文档与归档

- [ ] 9.1 更新 `kuship-console/CLAUDE.md` 在"集群基础信息透传（migrate-console-cluster-extras）"段后追加"组件监控指标透传（migrate-console-monitor-extras）"段：列 4 个新 endpoint、6 个 region method 路径、`ServiceMonitor` entity 字段集、与 cluster-extras `getClusterEvents` 的边界、与后续 `migrate-console-component-service-monitor` / `migrate-openapi-monitor-aggregate` 的衔接点；同步更新接口表 `MonitorOperations` 行 4/4 → 8/8
- [ ] 9.2 路线图 `migrate-region-coverage-roadmap` 的 Requirement 表中把 "Monitor 指标 + 资源中心事件" 行的 kuship 列由 4 改为 10、缺口由 6 改为 0 + 标注 `migrate-console-monitor-extras` 为已完成（归档时执行）
- [ ] 9.3 记录 §1.4 探测结果的最终 region URL 与 query string 编码策略到 `design.md` 决策 7"探测结果"小节，便于后续 component-service-monitor / monitor-aggregate 子 change 复用

## 10. 编译 / 重启 / 联动验证

- [ ] 10.1 `cd kuship-console && mvn -DskipTests package` 通过；同时 `mvn test` 在 monitor-extras 范围内 ≥ 13 用例全过（8 单测 + 5 集成测试 + 5 repository 测试）
- [ ] 10.2 重启 console；`curl -s -H "Authorization: GRJWT $TOKEN" "http://localhost:8080/console/teams/<team>/apps/<alias>/metrics" | jq .` 返 200 + `data.list`（**需用户本地起 console + region 后联动**）
- [ ] 10.3 `curl ... /console/teams/<team>/regions/<region>/resource-center/events?kind=Pod` 返 200 + `data.bean` 或 `data.list`（**需用户联动**）
- [ ] 10.4 `curl ... /console/teams/<team>/region/<region>/sort_domain/query?repo=1&page=1&page_size=5` 返 200 + `data.bean.total` + `data.list`（**需用户联动**）
- [ ] 10.5 `curl ... /console/teams/<team>/region/<region>/sort_service/query` 返 200 + `data.list`（**需用户联动**）
- [ ] 10.6 不存在的 service alias / team_name → 404 透传，本地不缓存（**需用户联动**）
- [ ] 10.7 启动 application 后观察日志确认 hibernate `validate` 模式对 `tenant_service_monitor` 表通过（无 schema mismatch warning）
