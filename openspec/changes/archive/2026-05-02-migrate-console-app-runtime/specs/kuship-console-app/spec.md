## ADDED Requirements

### Requirement: 组件生命周期动作端点

kuship-console SHALL 暴露与 rainbond-console 100% 兼容的组件生命周期端点，覆盖启动 / 停止 / 暂停 / 取消暂停 / 重启 / 部署 / 回滚 / 升级 共 8 个动作；每次调用 SHALL 先经 `@RequirePerm` 校验对应权限码（如 `APP_OVERVIEW_START` / `APP_OVERVIEW_STOP` 等），再委托 region API 触发异步任务，本地仅更新 `tenant_service.update_time` 与 `update_version`，不存储运行态。

#### Scenario: POST /console/teams/{team}/apps/{alias}/start 启动组件

- **WHEN** 用户对状态为 closed 的组件调 POST `/console/teams/team1/apps/grabc123/start`
- **THEN** kuship-console 校验 `APP_OVERVIEW_START` 权限通过
- **AND** 调用 `ServiceOperations.startService(region, tenantName, serviceAlias, userId)`
- **AND** region 返回 `event_id`
- **AND** kuship-console 把 `tenant_service.update_version += 1` 并保存
- **AND** 响应 `{"code":200,"data":{"bean":{"event_id":"<id>"}}}`

#### Scenario: POST /stop /restart /pause /unpause 等 7 兄弟端点

- **WHEN** 调用 `/stop`、`/pause`、`/unpause`、`/vm_web`（unpause 别名）、`/restart`、`/deploy`、`/rollback`、`/upgrade` 任一端点
- **THEN** 流程同 start，区别仅在 region 子 path 与权限码（stop=APP_OVERVIEW_STOP / restart=APP_OVERVIEW_RESTART / deploy=APP_OVERVIEW_DEPLOY / rollback=APP_UPGRADE / upgrade=APP_UPGRADE）
- **AND** 任一端点 region 4xx/5xx 时直接抛 `ServiceHandleException` 给前端

### Requirement: 组件扩缩容端点

kuship-console SHALL 实现 4 类扩缩容端点：垂直伸缩（vertical）/ 水平伸缩（horizontal）/ 通用 scaling / 部署类型切换（deploytype），事务内同时更新本地 `tenant_service` 配置字段并通知 region 重算 deployment spec。

#### Scenario: POST /vertical 修改 CPU/内存

- **WHEN** 调 `/console/teams/team1/apps/grabc123/vertical` body `{"new_memory":2048,"new_gpu":0,"new_cpu":1000}`
- **THEN** kuship-console 在事务内更新 `tenant_service.min_memory=2048, min_cpu=1000, container_gpu=0`
- **AND** 同事务调 region `/v2/tenants/team1/services/grabc123/vertical`
- **AND** region 失败时事务回滚，旧值保留

#### Scenario: POST /horizontal 修改副本数

- **WHEN** 调 `/console/teams/team1/apps/grabc123/horizontal` body `{"new_node":3}`
- **THEN** 更新 `tenant_service.min_node=3`
- **AND** 通知 region

#### Scenario: POST /scaling 一次同时改 CPU/内存/副本

- **WHEN** 调 `/scaling` body 同时含 `new_cpu`/`new_memory`/`new_node`/`new_gpu`
- **THEN** 一次事务内全部写本地 + 一次 region 调用

#### Scenario: PUT /deploytype 修改部署类型

- **WHEN** 调 `PUT /apps/grabc123/deploytype` body `{"extend_method":"stateful_singleton"}`
- **THEN** kuship-console 更新 `tenant_service.extend_method` 并通知 region

### Requirement: 批量动作端点

kuship-console SHALL 实现 `POST /console/teams/{team}/batch_actions` 端点，支持对一组 service_id（或 service_alias）一次性执行 start / stop / restart / deploy 动作，部分失败时返回 207 风格的 success/failed 列表。

#### Scenario: 全部成功

- **WHEN** 调 `/batch_actions` body `{"action":"start","service_ids":["s1","s2","s3"]}`
- **THEN** 按 region_name 分组分别调 region `/v2/tenants/{tenantName}/batchactions`
- **AND** 全部 region 调用成功时返回 `{"code":200,"data":{"bean":{"success":["s1","s2","s3"],"failed":[]}}}`

#### Scenario: 部分失败

- **WHEN** 其中一组 region 调用失败
- **THEN** 返回 `{"code":200,"data":{"bean":{"success":[...],"failed":[{"service_id":"s2","msg":"region error"}]}}}`
- **AND** 不抛异常，前端可逐条渲染状态

### Requirement: 组件删除增强端点

kuship-console SHALL 实现 3 个删除增强端点：批量软删除（batch_delete）/ 强制本地清理（again_delete）/ 应用整组删除（groupapp/{group_id}/delete），全部复用 `AppDeleteService` 的归档与本地清理逻辑。

#### Scenario: DELETE /batch_delete 批量软删除

- **WHEN** 调 `DELETE /console/teams/team1/batch_delete` body `{"service_ids":["s1","s2"]}`
- **THEN** 对每个 service_id 调用 `AppDeleteService.delete(...)`
- **AND** 每个组件先调 region 释放 K8s 资源，再归档 `tenant_service_delete`
- **AND** 任一失败时返回失败 service_id 列表，已成功的不回滚

#### Scenario: DELETE /again_delete 强制本地清理

- **WHEN** region 已不存在某 service，调 `/again_delete` body `{"service_ids":["s1"]}`
- **THEN** 跳过 region 调用，直接归档 + 清理本地表

#### Scenario: DELETE /groupapp/{group_id}/delete 整组删除

- **WHEN** 调 `DELETE /console/teams/team1/groupapp/42/delete`
- **THEN** kuship-console 列出 group_id=42 下所有 service_id，按批量软删除流程逐个处理
- **AND** 同时删除 `service_group` (group_id=42) 自身与所有 `service_group_relation`

### Requirement: 组件属性变更端点

kuship-console SHALL 实现 `PUT /apps/{alias}/change/service_name`、`PUT /apps/{alias}/set/is_upgrade`、`PUT /apps/{alias}/extend_method` 三个属性变更端点，每次仅修改本地一个标量字段，不调 region。

#### Scenario: PUT /change/service_name 修改组件名

- **WHEN** 调 body `{"service_name":"my-mysql","k8s_component_name":"my-mysql"}`
- **THEN** kuship-console 更新 `tenant_service.service_name` 与 `k8s_component_name`，不调 region

#### Scenario: PUT /set/is_upgrade 切换是否随 region 升级

- **WHEN** 调 body `{"is_upgrade":true}`
- **THEN** 更新 `tenant_service.build_upgrade=true`

#### Scenario: PUT /extend_method 修改伸缩模式

- **WHEN** 调 body `{"extend_method":"stateful_multiple"}`
- **THEN** 更新 `tenant_service.extend_method` 并校验值在 `{stateless_multiple, stateful_singleton, stateful_multiple, job, cronjob}` 集合内，否则 400

### Requirement: 组件状态与 Pod 查询端点

kuship-console SHALL 实现 `GET /apps/{alias}/status`、`GET /apps/{alias}/pods`、`POST /apps/{alias}/pods/detail`、`GET /apps/{alias}/pods/{pod_name}`、`GET /groups/{tenant_name}/{app_id}/pods/{pod_name}` 共 5 个状态/Pod 端点，全部由 region GET 透传，本地不持久化运行态。

#### Scenario: GET /apps/{alias}/status 返回单组件状态

- **WHEN** 调 `/console/teams/team1/apps/grabc123/status`
- **THEN** kuship-console 调 region `/v2/tenants/team1/services/grabc123/status` 透传响应
- **AND** 响应包含 `status`、`update_time`、`pod_num` 等字段

#### Scenario: GET /apps/{alias}/pods 返回 Pod 列表

- **WHEN** 调 `/apps/grabc123/pods`
- **THEN** kuship-console 调 region `/v2/tenants/team1/services/grabc123/pods` 拿到 Pod 列表
- **AND** 每个 Pod 含 `pod_name`、`pod_ip`、`status`、`container_image`、`start_time` 字段

#### Scenario: GET /pods/{pod_name} 返回 Pod 详情

- **WHEN** 调 `/apps/grabc123/pods/podname-xxx-yyy`
- **THEN** kuship-console 透传 region 详情，含 `containers[*]`、`events[*]`、`resource{cpu_limit,memory_limit}` 三段

### Requirement: 组件事件与日志端点

kuship-console SHALL 实现 `GET /apps/{alias}/events`、`GET /apps/{alias}/event_log`、`GET /apps/{alias}/log`、`GET /apps/{alias}/log_instance`、`GET /apps/{alias}/history_log`、`GET /apps/{alias}/logs`、`GET /teams/{team}/events`、`GET /teams/{team}/events/{eventId}/log`、`POST /log_proxy` 共 9 个端点，全部走 region HTTP 同步轮询，不引入 SSE / WebSocket。

#### Scenario: GET /apps/{alias}/events 列出事件分页

- **WHEN** 调 `/apps/grabc123/events?page=1&page_size=10`
- **THEN** 透传 region `/v2/tenants/team1/services/grabc123/events` 返回 `{list:[...], total:N}`

#### Scenario: GET /apps/{alias}/event_log 单事件详情

- **WHEN** 调 `/apps/grabc123/event_log?event_id=eid1234`
- **THEN** kuship-console 调 region `/v2/event_log` 透传 stdout 数组

#### Scenario: GET /apps/{alias}/log_instance 拿 WebSocket 直连凭证

- **WHEN** 调 `/apps/grabc123/log_instance`
- **THEN** kuship-console 调 region 拿到 `{host, path, token}` 三元组直接透传给前端
- **AND** 前端拿 token 自行 WebSocket 直连 region，不经过 console

#### Scenario: POST /log_proxy 通用日志代理

- **WHEN** 用户上传 region path body 调 `/log_proxy`
- **THEN** kuship-console 用 JWT 校验通过后透传到对应 region 同名 path

### Requirement: 组件监控查询端点

kuship-console SHALL 实现 `GET /apps/{alias}/monitor/query`、`GET /apps/{alias}/monitor/query_range`、`GET /groups/{group_id}/monitor/batch_query`、`GET /apps/{alias}/resource`、`GET /apps/{alias}/trace`、`POST /apps/{alias}/trace`、`DELETE /apps/{alias}/trace` 共 7 个监控端点，全部透传到 region 的 Prometheus 代理路径。

#### Scenario: GET /monitor/query 透传 PromQL

- **WHEN** 调 `/apps/grabc123/monitor/query?query=cpu_usage{service_id="x"}&time=1715000000`
- **THEN** kuship-console 透传到 region `/v2/tenants/team1/monitor/query` 同样的 query string
- **AND** 响应原样返回 region 响应（不重新包 ApiResult，保持 Prometheus JSON）

#### Scenario: GET /monitor/query_range 时间区间监控

- **WHEN** 调 `/monitor/query_range?query=...&start=...&end=...&step=15s`
- **THEN** 透传 region `/v2/tenants/team1/monitor/query_range`

#### Scenario: GET /groups/{group_id}/monitor/batch_query 批量

- **WHEN** 调 `/groups/42/monitor/batch_query?query=cpu`
- **THEN** kuship-console 列出 group_id=42 下所有 service_id，构造批量 query 调 region

#### Scenario: GET /apps/{alias}/resource 资源占用

- **WHEN** 调 `/apps/grabc123/resource`
- **THEN** kuship-console 调 region 拿到 `{cpu_used, memory_used, cpu_limit, memory_limit}` 透传

### Requirement: 弹性伸缩规则 CRUD 端点

kuship-console SHALL 实现 `GET/POST /apps/{alias}/xparules`、`GET/PUT /apps/{alias}/xparules/{rule_id}`、`GET /apps/{alias}/xparecords` 共 5 个 autoscaler 端点；规则数据 SHALL 双写本地 `autoscaler_rules` + `autoscaler_rule_metrics` 两表，并同步至 region；伸缩历史（xparecords）SHALL 由 region 持有，console 仅做 GET 透传。

#### Scenario: POST /xparules 创建自动伸缩规则

- **WHEN** 调 `/apps/grabc123/xparules` body `{"min_replicas":1,"max_replicas":5,"metrics":[{"metric_type":"resource_metrics","metric_name":"cpu","metric_target_type":"average_value","metric_target_value":500}]}`
- **THEN** kuship-console 在事务内 INSERT `autoscaler_rules` 1 行 + `autoscaler_rule_metrics` 1 行
- **AND** 同事务 POST region `/v2/tenants/team1/services/grabc123/xparules`
- **AND** region 失败时事务回滚

#### Scenario: GET /xparules 列出本组件全部规则

- **WHEN** 调 `/apps/grabc123/xparules`
- **THEN** kuship-console 仅查本地 `autoscaler_rules` 与 `autoscaler_rule_metrics` 关联返回，不调 region

#### Scenario: PUT /xparules/{rule_id} 修改规则

- **WHEN** 调 PUT body 同创建
- **THEN** kuship-console 更新本地 → PUT region；region 失败回滚

#### Scenario: GET /xparules/{rule_id} 单个规则详情

- **WHEN** 调 GET
- **THEN** 仅查本地，返回规则基础信息 + metrics 数组

#### Scenario: GET /xparecords 历史伸缩记录

- **WHEN** 调 `/apps/grabc123/xparecords`
- **THEN** 透传 region 返回的伸缩事件历史，console 不存

### Requirement: 应用拓扑与访问入口端点

kuship-console SHALL 实现 `GET /groups/{group_id}/topological`、`GET /groups/{group_id}/topological/internet`、`GET /apps/{alias}/visit`、`GET /groups/{group_id}/visit`、`GET /service_alarm` 共 5 个查询端点，全部 region 透传 + 本地 group/component 元数据 enrich。

#### Scenario: GET /groups/{group_id}/topological 返回拓扑图

- **WHEN** 调 `/console/teams/team1/groups/42/topological`
- **THEN** kuship-console 调 region 拿到 service 节点+依赖边，本地补 service_cname / icon / extend_method 三个字段

#### Scenario: GET /apps/{alias}/visit 返回单组件访问入口

- **WHEN** 调 `/apps/grabc123/visit`
- **THEN** 透传 region 返回 `{access_urls:[...], domain_urls:[...]}`

### Requirement: 运行时模块新增 4 张表的 JPA Entity 与 Repository

kuship-console SHALL 新增 `AutoscalerRule`（autoscaler_rules）、`AutoscalerRuleMetric`（autoscaler_rule_metrics）两个 JPA Entity 与对应 Repository，主键类型统一使用 `Integer`（与 Django INT 4 字节对齐），存放于 `cn.kuship.console.modules.appruntime.entity` 与 `repository` 包；与 region 表名/列名严格对齐，不引入新 schema。

#### Scenario: AutoscalerRule Entity 映射 autoscaler_rules 表

- **WHEN** Hibernate 启动加载 entity
- **THEN** `AutoscalerRule.@Table(name="autoscaler_rules")` 含 `rule_id`(32 char UUID)、`service_id`、`enable`、`xpa_type`、`min_replicas`、`max_replicas`、`create_time`
- **AND** PK `id` 为 Integer 自增

#### Scenario: AutoscalerRuleMetric Entity 映射 autoscaler_rule_metrics 表

- **WHEN** Hibernate 启动加载 entity
- **THEN** `AutoscalerRuleMetric.@Table(name="autoscaler_rule_metrics")` 含 `rule_id`、`metric_type`、`metric_name`、`metric_target_type`、`metric_target_value`
- **AND** 通过 `rule_id`（非 FK，逻辑关联）与 AutoscalerRule 关联

#### Scenario: ddl-auto=validate 启动通过

- **WHEN** 应用启动连真实 MySQL（rainbond docker compose 已建表）
- **THEN** Hibernate ddl-auto=validate 不报缺列错误

### Requirement: 14 接口骨架的 4 个运行时子接口实现

kuship-console SHALL 实现 14 接口骨架中已声明的 4 个运行时子接口共 22 method：
- `ServiceLifecycleOperations` 10 method（startService/stopService/restartService/upgradeService/rollback/horizontalUpgrade/verticalUpgrade/changeMemory/pauseService/unpauseService）
- `ServiceStatusOperations` 6 method（serviceStatus/checkServiceStatus/getServicePods/podDetail/getDynamicServicesPods/getUserServiceAbnormalStatus）
- `ServiceLogOperations` 3 method（getServiceLogs/getServiceLogFiles/getDockerLogInstance）
- `EventOperations` 3 method（getEventLog/getTargetEventsList/getMyteamsEventsList）

实现位置 `cn.kuship.console.modules.appruntime.api.*Impl`，每个 Impl 标注 `@Service @Primary` 替换 14 接口骨架的默认 unsupported 实现。

#### Scenario: 10 个生命周期 method 覆盖

- **WHEN** 调用 `ServiceLifecycleOperations.startService` / `stopService` / `restartService` / `upgradeService` / `rollback` / `horizontalUpgrade` / `verticalUpgrade` / `changeMemory` / `pauseService` / `unpauseService`
- **THEN** 每个 method 用 `RegionApiSupport.exchange(lambda)` 模板包装 RestClient 调用
- **AND** path 模式统一为 `/v2/tenants/{teamName}/services/{serviceAlias}/<action>`

#### Scenario: 6 个状态/Pod method 覆盖

- **WHEN** 调用 `ServiceStatusOperations.serviceStatus` / `checkServiceStatus` / `getServicePods` / `podDetail` / `getDynamicServicesPods` / `getUserServiceAbnormalStatus`
- **THEN** 全部使用 `RestClient.GET` 或 `RestClient.POST` 透传响应

#### Scenario: 6 个日志/事件 method 覆盖

- **WHEN** 调用 `ServiceLogOperations.getServiceLogs` / `getServiceLogFiles` / `getDockerLogInstance` 与 `EventOperations.getEventLog` / `getTargetEventsList` / `getMyteamsEventsList`
- **THEN** 全部使用 `RestClient.GET` 透传响应；query string 由 Map<String,Object> body 序列化拼接

### Requirement: 新增 MonitorOperations 与 AutoscalerOperations 两接口

kuship-console SHALL 在 `infrastructure.region.api` 包下新增 `MonitorOperations`（4 method：query / queryRange / batchQuery / getServiceResources）与 `AutoscalerOperations`（4 method：createRule / updateRule / deleteRule / listScalingRecords）两个接口及对应 `@Primary @Service` 实现类，共享 `RegionApiSupport` helper。

#### Scenario: MonitorOperations 4 method 全部透传

- **WHEN** controller 调用 `monitorOperations.query(region, teamName, queryString)`
- **THEN** Impl 使用 `RestClient.get().uri(...).retrieve()` 透传 query string，并把响应反序列化为 `Map<String,Object>`

#### Scenario: AutoscalerOperations 4 method 全部对应 region xparules CRUD

- **WHEN** controller 调用 `autoscalerOperations.createRule(region, teamName, serviceAlias, body)`
- **THEN** Impl 调 region `/v2/tenants/{tenantName}/services/{serviceAlias}/xparules` POST
- **AND** 同样模式覆盖 PUT / DELETE / GET records

### Requirement: 运行时模块测试覆盖

kuship-console SHALL 提供至少 4 类集成测试覆盖运行时核心：
1. `AppLifecycleIntegrationTest`：start/stop/restart 端点对 mock RestClient 的请求路径与权限校验断言；
2. `AppScalingIntegrationTest`：vertical/horizontal/scaling 写本地 + region 调用断言；
3. `AppAutoscalerIntegrationTest`：xparules POST/GET/PUT 双写本地 autoscaler_rules + autoscaler_rule_metrics；
4. `AppEventLogIntegrationTest`：events / event_log / log / log_instance 透传响应字段保留断言。

#### Scenario: 集成测试全部使用真实 MySQL

- **WHEN** 在 docker-compose 启动后跑 `mvn -Dtest='cn.kuship.console.modules.appruntime.**' test`
- **THEN** 每类测试在 `@BeforeAll` 用高位 user_id（9090xx）插入 user/team/service 数据
- **AND** 在 `@AfterAll` 清理避免数据残留
- **AND** 全部用例通过

#### Scenario: Region API 用 MockRestClient 桩

- **WHEN** 测试不依赖真实 region
- **THEN** 测试类用 `@MockBean` 替换 `ServiceOperations` / `MonitorOperations` / `AutoscalerOperations`
- **AND** 用 `Mockito.when(...).thenReturn(...)` 桩响应
