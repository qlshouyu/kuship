## Why

kuship-console 已实现应用创建（image / source_code / third_party），但创建后的 **运行时管理** 完全缺失：用户无法启动 / 停止 / 重启 / 部署 / 升级 / 回滚组件，看不到组件状态、事件、Pod、日志、监控数据，也无法配置弹性伸缩规则。这是用户走完"创建 → 跑起来"路径的最后一公里，没有它整套控制台只能看不能用。

本次迁移将 rainbond-console 的 6 个 view 文件（`app_manage.py`、`app_event.py`、`app_monitor.py`、`pod.py`、`app_autoscaler.py`、`log_proxy.py` + `app_overview.py` 状态部分）共 **40+ 接口**完整搬到 kuship-console。

## What Changes

- **生命周期动作**：`/start`、`/stop`、`/pause`、`/unpause`、`/vm_web`（unpause 别名）、`/restart`、`/deploy`、`/rollback`、`/upgrade` —— POST 触发 region 异步动作，本地不持久化新表，仅写 `update_time` / `update_version`。
- **垂直/水平/通用扩缩容**：`/vertical`、`/horizontal`、`/scaling`、`/extend_method` —— 写 `tenant_service.min_cpu/min_memory/min_node/extend_method` 并通知 region。
- **批量动作**：`/batch_actions`（POST）支持对多个 service_alias 一次性执行 start/stop/restart/deploy。
- **删除增强**：`/batch_delete`、`/again_delete`、`/groupapp/{group_id}/delete` —— 批量软删除 + 应用整体删除。
- **类型与名称变更**：`/deploytype`、`/change/service_name`、`/set/is_upgrade` —— 修改 service_type / service_name / build_upgrade 字段。
- **状态查询**：`/AppStatusView`（GET 单组件状态）、`/AppGroupView` 状态汇总、`/topological/internet`、`/topological` 拓扑视图、`/groups/{group_id}/visit`、`/apps/{alias}/visit`（访问入口）。
- **Pod 列表与详情**：`/pods`（POST/GET，复用 app_overview.py 的 ListAppPodsView）、`/groups/{app_id}/pods/{pod_name}`（ApplicationPodView）、`/apps/{alias}/pods/{pod_name}`（AppPodsView）—— 返回容器、resource、status。
- **事件流**：`/events`（GET 列表 + 分页）、`/event_log`（GET 单事件日志）、`/teams/{team}/events`（团队级事件聚合）、`/teams/{team}/events/{eventId}/log`。
- **运行日志**：`/log`（GET 当前 Pod 实时尾部日志）、`/log_instance`（GET 实例日志地址换 ws token）、`/history_log`（GET 历史日志文件列表）、`/logs`（ComponentLogView 替代）、`/log_proxy`（POST 通用日志代理给 region）。
- **监控**：`/monitor/query`、`/monitor/query_range`（Prometheus 透传）、`/groups/{group_id}/monitor/batch_query`（批量组件 metrics）、`/resource`（资源占用）、`/trace`（链路追踪 GET / POST / DELETE）、`/internal-graphs`、`/exchange-graphs`、`/graphs`（CRUD）、`/graphs/{graph_id}`（RUD）、`/metrics`（自定义指标）。
- **弹性伸缩规则**：`/xparules`（GET 列表 / POST 创建）、`/xparules/{rule_id}`（GET / PUT 详情）、`/xparecords`（GET 历史伸缩记录）—— 引入 `service_payment_notify`/`autoscaler_rules`/`autoscaler_rule_metrics` 三表（沿用 rainbond schema）。
- **Region API 扩展**：`ServiceOperations` 新增 ~15 个方法（startService / stopService / restartService / deployService / upgradeService / rollbackService / changeMemory / horizontal / scaling / batchActions / getServiceStatus / getServiceLogs / getServiceEvents / getServicePods / etc.），打通 region grctl `/v2/tenants/{tenantName}/services/{serviceAlias}/lifecycle` 等子路径。
- **Autoscaler 子资源**：新建 `AutoscalerRule` / `AutoscalerRuleMetric` Entity，本地 CRUD + region 双写策略（先 console 后 region，删除反序）。

## Capabilities

### Modified Capabilities

- `kuship-console-app`: 新增 ~15 条运行时相关 Requirement —— 生命周期动作 / 状态查询 / 扩缩容 / 批量动作 / 事件流 / 运行日志 / 监控查询 / 拓扑 / Pod 详情 / 弹性伸缩规则 / Region API 运行时方法集；删除归档保持原状不修改。

## Impact

- **新增包**：`cn.kuship.console.modules.appruntime/`（controller + service + entity + repository + dto），与 `application/` 平级；`appruntime` 复用 `application/` 已有的 `TenantService`、`TenantServiceEnvVar`、`ServiceGroup` Entity。
- **新增 Entity**：`AutoscalerRule`（autoscaler_rules）、`AutoscalerRuleMetric`（autoscaler_rule_metrics）、`ServiceMonitor`（service_monitor）、`ComponentGraph`（component_graph）—— 共 4 张表的 JPA 映射。
- **新增 Region API 方法**：`ServiceOperations` 增至 ~22 method（已有 7 + 新增 15）；新增 `MonitorOperations`（query/query_range/batch_query 三个），`AutoscalerOperations`（rule CRUD），与 `kuship-console-app` 已声明的 14 接口骨架一致演进。
- **依赖**：保持现有 `spring-boot-starter-webmvc` + `spring-boot-starter-data-jpa` + `RestClient`；不引入新第三方库（不上 WebSocket、不上 SSE：log/event 跟 rainbond 一样保持 JSON 行尾轮询，避开 graalvm-native 兼容性风险）。
- **测试**：扩展 4 个集成测试覆盖核心生命周期 + 监控代理 + 自动伸缩规则 CRUD。
