## Context

第 7 阶段（migrate-console-app-create）让 kuship-console 能"造出"组件，但创建后到底怎么"跑"——start / stop / restart / 弹缩 / Pod / 日志 / 事件 / 监控——还全部停留在 rainbond-console。本阶段是把"装好的发动机踩油门"的部分接过来，目标是让 kuship-ui 的"组件总览"页能完全脱离 rainbond-console 后端工作。

涉及参考代码：
- `console/views/app_manage.py` 1119 行（生命周期 + 扩缩容 + 批量动作 + 删除增强 + 名称/类型/升级开关变更，共 22 个 view 类）
- `console/views/app_event.py` 321 行（events / event_log / log / log_instance / history_log / 团队级 events 6 view）
- `console/views/app_monitor.py` 306 行（query / query_range / batch_query / resource / trace / 公开 monitor 7 view）
- `console/views/pod.py` 27 行（apps/{alias}/pods/{pod_name}）
- `console/views/app_autoscaler.py` 144 行（xparules CRUD + xparecords，3 view）
- `console/views/log_proxy.py` 168 行（统一日志代理，1 view）
- `console/views/services_toplogical.py` 131 行（拓扑/internet 入口，3 view）
- `console/views/app_overview.py` 部分（AppStatusView / ListAppPodsView / AppGroupView 等 ~6 view，与已迁移 group/component 不冲突的部分）

总 endpoint 计 **40+**。所有 endpoint 都共享团队/组件基础校验（findByTenantName + findByTenantIdAndServiceAlias）以及 `@RequirePerm` 权限注解，模式与第 4-7 阶段完全一致，可复用 `PermService` / `RequestContext` / `LegacyPasswordEncoder` 等基建。

## Goals / Non-Goals

**Goals:**

- 提供 100% URL 兼容的运行时管理 API，让 kuship-ui 的"应用详情/总览/Pod/日志/监控/伸缩"四个 tab 能完全替换 rainbond-console。
- 把 ServiceOperations 接口从已有的 7 method 扩展到 ~22 method（生命周期 + 扩缩容 + 状态/Pod/Log/Event 查询 + 批量动作）。
- 引入 `MonitorOperations`（3 method）和 `AutoscalerOperations`（3 method）两个新 region 子接口，遵循同一 RegionApiSupport 模板。
- 落地 `AutoscalerRule` / `AutoscalerRuleMetric` 两张表的本地 CRUD + 双写 region。
- 保持 graalvm-native 兼容性：不引入 SSE / WebSocket / Reactor，日志/事件全部走 region 同步 GET 返回 JSON 数组（rainbond 原版即如此）。

**Non-Goals:**

- 不实现真正的 WebSocket 实时日志流（rainbond 是浏览器直连 region 拿 ws-token 后自连，console 只发 token，不做流转）。
- 不实现 Helm 应用的 helm-status / helm-uninstall（属于 app-market 阶段）。
- 不实现备份/迁移/导入/导出（属于 app-market 阶段）。
- 不实现 Webhook 触发部署 (`/webhooks/{service_id}`、`/image/webhooks/...`)（属于 misc 阶段）。
- 不实现 KubeBlocks 数据库集群 detail / backup / parameters（属于 misc 阶段）。
- 不实现 Job/CronJob 策略（`/job_strategy`，留给 misc 阶段）。
- 不实现 service_monitor / labels / metrics 自定义图表 CRUD（留作下一个增量 change，作用域上属于"高级监控"，先把基础监控查询接通）。

## Decisions

### 决策 1：生命周期接口走"先 region 后本地"半幂等

`start / stop / restart / pause / unpause / deploy / rollback / upgrade` 都是 region 异步任务：

- **流程**：检查本地 service 存在 → POST 至 region `/v2/tenants/{teamName}/services/{serviceAlias}/lifecycle` （或具体子路径如 `start`/`stop`/`pause`/`unpause`/`restart`）→ 拿到 region 返回的 event_id → 本地仅 `update_time = now()` + `update_version += 1` → 返回 `bean.event_id`。
- **失败语义**：region 4xx/5xx 直接抛 `ServiceHandleException`（不写本地）；region 通了但是写本地异常时由 `@Transactional` 回滚（实际这里只 SAVE 一行，影响极小）。
- **幂等保证**：region 的 lifecycle 已自带幂等，console 不重复防抖；前端短时双击 region 自己挡。

**Why not 本地先写再 region**：start/stop 改的是"运行时状态"而不是"配置态"，本地没有运行态字段（status 字段查询时 always 实时拉 region），先发 region 再记 audit log 是 rainbond 已有惯例，保持一致。

### 决策 2：扩缩容写本地配置 + 通知 region

`vertical / horizontal / scaling / extend_method / set/is_upgrade` 修改的是 `tenant_service` 表的 `min_cpu / min_memory / min_node / extend_method / build_upgrade` 字段：

- **流程**：本地先 update tenant_service（事务内）→ 同事务内 POST 至 region `/v2/tenants/{teamName}/services/{serviceAlias}/{vertical|horizontal|...}` → 通知 region 重算 deployment spec → 提交。
- **回滚**：region 失败时 console 回滚事务，状态保持原值，前端会重新拉到旧 min_node 显示。
- **deploytype**：修改 service_type（stateless/stateful），同样路径，把 service_type 一并发给 region。

### 决策 3：批量动作复用 ServiceOperations.batchActions

`/teams/{team_name}/batch_actions` 一次性接收 `{action: "start"|"stop"|"restart"|"deploy", service_ids: [...]}`：

- 本地查询 `tenant_service.findAllByServiceIdIn(serviceIds)` 拿 region_name + 校验所属 tenant；
- 按 region_name 分组，分别调 region `/v2/tenants/{teamName}/batchactions`；
- 任一 region 失败时返回 207 部分成功 JSON：`{success: [...], failed: [{service_id, msg}]}`，前端按 service_id 渲染状态。

### 决策 4：删除增强（batch_delete + again_delete + groupapp/{group_id}/delete）

- `/batch_delete`：批量软删除，等价于循环调 `AppDeleteService.delete()`，一次事务+一次 region 调用。
- `/again_delete`：用于 region 已不存在时强制清理本地（`tenant_service_delete` 中 service_id 同名的脏数据），不调 region。
- `/groupapp/{group_id}/delete`：按 application 整组级删除，先列出 group 下所有 service，再走 batch_delete 链路。

复用现有 `AppDeleteService`（在 appcreate 模块）+ 新增 `AppBatchDeleteService`（在 appruntime 模块），不重复实现归档逻辑。

### 决策 5：状态查询 / Pod / 拓扑 / 访问入口 全部纯查询

- `/AppStatusView`、`/ListAppPodsView`、`/AppGroupView`(GET status)、`/groups/{group_id}/visit`、`/topological`、`/topological/internet`、`/apps/{alias}/visit`、`/apps/{alias}/pods/{pod_name}`、`/groups/{tenant}/{app_id}/pods/{pod_name}`：
- 全部仅做 region GET 透传 + 必要的本地组件名/版本字段 enrich，不写库；
- 可全部走 `RegionApiSupport.getJson(...)` 同一 helper，签名 `Map<String,Object> getJson(regionName, path)`。

### 决策 6：事件流 + 历史日志走轮询，不上 SSE

- `/events`（list 分页）：调 region `/v2/tenants/{teamName}/services/{serviceAlias}/events?page&page_size`；
- `/event_log`（单事件）：调 region `/event_log?event_id=` 返回 JSON 数组（每行一条 stdout）；
- `/log`（实时尾部）：调 region `/log?lines=` 返回最近 N 行；
- `/log_instance`（拿 ws token）：调 region `/log_instance` 拿到 `host + path + token`，原样回前端，由前端 WS 直连 region；
- `/history_log`（文件列表）：调 region `/history_log` 返回 `[{file_name, file_size, time}]`；
- `/teams/{team}/events`（团队聚合）和 `/teams/{team}/events/{eventId}/log`：region 团队级 path。

**Why no SSE**：rainbond 原版就是无状态轮询，graalvm-native 不喜欢 reactor-netty，保持一致。

### 决策 7：监控 query / query_range 透传 PromQL

- `/monitor/query`、`/monitor/query_range`、`/groups/{group_id}/monitor/batch_query`：直接透传 query string 到 region `/v2/tenants/{teamName}/monitor/[query|query_range|batch_query]`，并由 `MonitorOperations` 接口承载。
- `/resource`：调 region `/v2/tenants/{teamName}/services/{serviceAlias}/resources`；返回容器 CPU/内存使用率。
- `/trace`：GET / POST / DELETE 透传 region `/v2/tenants/{teamName}/services/{serviceAlias}/trace`。
- 不实现 `/internal-graphs`、`/exchange-graphs`、`/graphs` CRUD（自定义图表用户量小，留给后续 monitor 增量）。

### 决策 8：弹性伸缩规则双写

`autoscaler_rules` + `autoscaler_rule_metrics` 两表是 console 主权数据：

- **创建**：本地先 INSERT autoscaler_rules + 多条 autoscaler_rule_metrics → POST region `/xparules`；region 失败 → 事务回滚。
- **更新**：本地先 UPDATE → PUT region；region 失败 → 回滚。
- **删除**：先 DELETE region → 再 DELETE 本地（出错时本地保留供重试）。
- **list**：纯本地查询；不用 region 兜底，避免 region 抖动失序。
- **xparecords**：region 持有，本地不存；GET 透传。

PK 用 Integer（与 Django 默认 INT 4 字节对齐，与第 4-7 阶段一致）；rule_id 是 32-char UUID（等同 service_id 风格），不要用 PK 暴露给外。

### 决策 9：service_alias 全局唯一假设

部分批量接口（batch_actions、batch_delete）用 `service_id_list` 作为入参；但 batch_actions rainbond 也接受 `service_alias_list`。我们两种都接受，做并集解析，后端按 `service_id` 统一处理，避免 alias 跨 tenant 重名歧义。

### 决策 10：Region 接口骨架最终形态

```
ServiceOperations              (从 7 → 22 method)
├── createService / updateService / deleteService          已有
├── buildService / codeCheck / getServiceLanguage / getServiceInfo  已有
├── startService / stopService / restartService            ▲ 新增
├── pauseService / unpauseService                           ▲ 新增
├── deployService / rollbackService / upgradeService        ▲ 新增
├── verticalUpgrade / horizontalUpgrade / scaling           ▲ 新增
├── changeDeployType                                        ▲ 新增
├── batchActions / batchDelete                              ▲ 新增
├── getServiceStatus / getServicePods / getServiceLogs     ▲ 新增
└── getServiceEvents / getServiceEventLog                   ▲ 新增

MonitorOperations              (新增 4 method)
├── query / queryRange / batchQuery
└── getServiceResources

AutoscalerOperations           (新增 4 method)
├── createRule / updateRule / deleteRule
└── listScalingRecords
```

每个新 method 用 `RegionApiSupport.execute(lambda)` 模板（与 ServicePortOperationsImpl 已有写法一致）。

## Risks / Trade-offs

- **[Risk]** ServiceOperations 接口面变大（22 method）→ Mitigation：拆出 service `lifecycle` / `scaling` / `inspect` 三个内部 helper，避免单文件过千行；`appruntime` controller 直接注 ServiceOperations，不强行抽中间 facade。
- **[Risk]** 批量动作 region 部分失败时 207 解析复杂 → Mitigation：先做"全成功"路径，部分失败仅返回错误条目，不要求事务级"原子批量"。
- **[Risk]** Pod 详情接口在不同路径下三份（pods.py / app_overview.py.ListAppPodsView / app_event.py 旁路）→ Mitigation：用同一个 `PodOperations.get(regionName, teamName, serviceAlias, podName)` 实现，三个 controller 都调它，绕过路径混乱。
- **[Risk]** Autoscaler 表与 region YAML CR 双写 →未来 region reconcile 漂移 → Mitigation：本阶段不解决 reconcile，所有 list 仅显示本地副本；`docs/runtime-troubleshooting.md` 留排查指引（推迟到 hardening change）。
- **[Risk]** `/log` 实时拉取会阻塞 console 线程池 → Mitigation：调 region 时 `RestClient.timeout(5s)`，超时直接返回 `{logs: []}` 不报错。
- **[Risk]** 团队级 events 聚合 path（`/teams/{team}/events`）跨多 service，分页需要在 region 端做 → Mitigation：纯透传，不做 console 分页二次合并。
- **[Trade-off]** 不实现自定义 graphs / labels / service_monitor → 这些是高级用户功能，使用率 <5%，并入下一个 monitor-advanced change，更聚焦本阶段 80% 主流程。

## Migration Plan

1. **阶段 A（基础设施）**：`ServiceOperations` 扩 15 method 接口骨架 + 默认 `RegionApiSupport` 实现；新增 `MonitorOperations` / `AutoscalerOperations` 两接口。
2. **阶段 B（生命周期 + 扩缩容）**：`AppLifecycleController` + `AppScalingController` + `AppPropertyController`（type/name/upgrade 开关）。
3. **阶段 C（状态 + Pod + 拓扑 + 访问）**：`AppStatusController` + `AppPodController` + `AppTopologyController` + `AppVisitController`。
4. **阶段 D（事件 + 日志）**：`AppEventController` + `AppLogController` + `LogProxyController`（团队级聚合 + 单事件日志 + history_log + log_proxy POST）。
5. **阶段 E（监控）**：`AppMonitorController` 透传 query / query_range / batch_query / resource / trace。
6. **阶段 F（弹性伸缩）**：`AutoscalerRule` + `AutoscalerRuleMetric` Entity + Repository + `AppAutoscalerController` （xparules CRUD + xparecords）。
7. **阶段 G（批量 + 删除增强）**：`AppBatchActionsController` + `AppBatchDeleteController` + `AppGroupDeleteController`。
8. **阶段 H（集成测试）**：4 类集成测试 —— lifecycle / scaling / autoscaler / events-log；针对真实 region 接通的部分仅 mock RestClient。

回滚策略：阶段失败时 `git revert` 即可，无 schema migration（autoscaler 两表已存在于 rainbond schema，console 只读写），不会破坏 rainbond-console。

## Open Questions

- **(Q1)** kuship-ui 当前是否只用 `xparules` 还是同时用了 `service_monitor`？如只用 xparules，则本阶段不必引入 service_monitor entity。
- **(Q2)** `/log_proxy`（log_proxy.py POST）是否仍被前端用？如已废弃则不实现，节省 60 LOC。
- **(Q3)** 是否需要在 console 端 cache region status 5 秒？rainbond 原版没有 cache，前端轮询 10s 一次，量级可接受。本阶段先不 cache，跑顺再优化。

不阻塞实施，做的过程中按"不实现"默认值前进，遇到联调失败再补。
