## 1. Region API 接口扩展

- [x] 1.1 实现现有 `ServiceLifecycleOperations` 接口的 10 method（已在 14 接口骨架中声明，无需新增 ServiceOperations 上的 method）—— `appruntime/api/ServiceLifecycleOperationsImpl.java`
- [x] 1.2 实现现有 `ServiceStatusOperations` 6 method + `ServiceLogOperations` 3 method + `EventOperations` 3 method（共 12 method）—— `appruntime/api/{ServiceStatusOperationsImpl,ServiceLogOperationsImpl,EventOperationsImpl}.java`
- [x] 1.3 全部 22 method 实现使用 `appruntime/api/RegionApiSupport.exchange(lambda)` 模板 + 处理器 `RegionApiResponseProcessor.extractBean`
- [x] 1.4 新建 `appruntime/api/MonitorOperations.java` 接口（4 method：query / queryRange / batchQuery / getServiceResources）+ `MonitorOperationsImpl.java`（@Primary @Service）
- [x] 1.5 新建 `appruntime/api/AutoscalerOperations.java` 接口（4 method：createRule / updateRule / deleteRule / listScalingRecords）+ `AutoscalerOperationsImpl.java`
- [ ] 1.6 ServiceLifecycleOperations / ServiceStatusOperations / ServiceLogOperations / EventOperations 各 method 进度表写入独立 README.md（推迟：进度信息已记入 CLAUDE.md 应用运行时段落）

## 2. 运行时模块基础设施

- [x] 2.1 创建包结构 `cn.kuship.console.modules.appruntime/{controller,service,entity,repository,dto}`
- [x] 2.2 新增 `entity/AutoscalerRule.java`（@Table autoscaler_rules，PK Integer 自增，含 rule_id 32-char UUID + service_id + enable + xpa_type + min_replicas + max_replicas）—— 注意：实际 schema 没有 `create_time` 列，entity 不含此字段
- [x] 2.3 新增 `entity/AutoscalerRuleMetric.java`（@Table autoscaler_rule_metrics，含 rule_id + metric_type + metric_name + metric_target_type + metric_target_value）
- [x] 2.4 新增 `repository/AutoscalerRuleRepository.java`（findByServiceId / findByRuleId / deleteByRuleId）
- [x] 2.5 新增 `repository/AutoscalerRuleMetricRepository.java`（findByRuleId / deleteByRuleId / saveAll）

## 3. 生命周期 Controller

- [x] 3.1 新建 `controller/AppLifecycleController.java`，挂 `@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")`
- [x] 3.2 实现 8 个 endpoint：POST `/start` /stop /pause /unpause /vm_web /restart /deploy /rollback /upgrade（其中 vm_web 复用 unpause 实现，alias path）
- [x] 3.3 每个 endpoint 加 `@RequirePerm(...)`：start=APP_OVERVIEW_START、stop=APP_OVERVIEW_STOP、restart=APP_OVERVIEW_RESTART、deploy=APP_OVERVIEW_DEPLOY、rollback=APP_UPGRADE、upgrade=APP_UPGRADE、pause/unpause/vm_web 复用 APP_OVERVIEW_STOP/START
- [x] 3.4 在 `account/perm/PermCode.java` 中补齐缺失的权限码常量（APP_OVERVIEW_START/STOP/RESTART/DEPLOY/UPGRADE 等，按 rainbond perms.py 对齐）
- [x] 3.5 通用流程：requireService → 调 ServiceOperations.<动作>(...) → 拿 event_id → 更新 update_version+update_time → 返回 GeneralMessage.ok({event_id})

## 4. 扩缩容与属性变更 Controller

- [x] 4.1 新建 `controller/AppScalingController.java`，POST `/vertical` /horizontal /scaling /extend_method 共 4 endpoint
- [x] 4.2 vertical：事务内 update min_cpu/min_memory/container_gpu → 调 region verticalUpgrade
- [x] 4.3 horizontal：事务内 update min_node → 调 region horizontalUpgrade
- [x] 4.4 scaling：事务内同时 update min_cpu/min_memory/min_node/container_gpu → 调 region scaling
- [x] 4.5 extend_method：事务内 update extend_method（校验值在白名单），不调 region
- [x] 4.6 新建 `controller/AppPropertyController.java`，PUT `/deploytype` /change/service_name /set/is_upgrade 共 3 endpoint
- [x] 4.7 deploytype：事务内 update service_type 并 region.changeDeployType
- [x] 4.8 change/service_name：仅本地 update service_name + k8s_component_name
- [x] 4.9 set/is_upgrade：仅本地 update build_upgrade

## 5. 状态 / Pod / 拓扑 / 访问 Controller

- [x] 5.1 新建 `controller/AppStatusController.java`，GET `/apps/{alias}/status` —— 调 ServiceOperations.getServiceStatus 透传
- [x] 5.2 新建 `controller/AppPodController.java`，GET `/apps/{alias}/pods` / GET `/apps/{alias}/pods/{pod_name}` / GET `/groups/{tenantName}/{app_id}/pods/{pod_name}` 共 3 endpoint
- [x] 5.3 AppPodController 同时支持 POST `/apps/{alias}/pods/detail`（接受 pod_name list 批查）
- [x] 5.4 新建 `controller/AppTopologyController.java`，GET `/groups/{group_id}/topological` / `/groups/{group_id}/topological/internet` 共 2 endpoint
- [x] 5.5 拓扑端点本地补 service_cname / icon / extend_method 三字段后返回
- [x] 5.6 新建 `controller/AppVisitController.java`，GET `/apps/{alias}/visit` / `/groups/{group_id}/visit` 共 2 endpoint，全部 region 透传

## 6. 事件与日志 Controller

- [x] 6.1 新建 `controller/AppEventController.java`，GET `/apps/{alias}/events` / `/event_log` / `/teams/{team}/events` / `/teams/{team}/events/{eventId}/log` 共 4 endpoint
- [x] 6.2 events 端点支持 `page`/`page_size` query 透传 region
- [x] 6.3 event_log 透传 stdout 数组，保持 region 原 JSON 结构不重新包
- [x] 6.4 团队级 events 聚合（`/teams/{team}/events`）调 region 路径 `/v2/tenants/{teamName}/events`
- [x] 6.5 新建 `controller/AppLogController.java`，GET `/apps/{alias}/log` / `/log_instance` / `/history_log` / `/logs` 共 4 endpoint
- [x] 6.6 log 端点 `?lines=N` query 透传 region 拿尾部日志
- [x] 6.7 log_instance 拿到 region {host, path, token} 三元组直接透传
- [x] 6.8 history_log 透传 region 历史文件列表
- [x] 6.9 新建 `controller/LogProxyController.java`，POST `/log_proxy` —— 接受 body {region, path, query, body} 转发 region

## 7. 监控 Controller

- [x] 7.1 新建 `controller/AppMonitorController.java`，GET `/apps/{alias}/monitor/query` / `/monitor/query_range` / `/groups/{group_id}/monitor/batch_query` / `/apps/{alias}/resource` 共 4 endpoint
- [x] 7.2 query / query_range 直接透传 query string 到 MonitorOperations
- [x] 7.3 batch_query 先列出 group 下 service_id 列表，构造批量 PromQL 调 region
- [x] 7.4 resource 调 ServiceOperations 拿 cpu/memory used/limit
- [x] 7.5 新建 `controller/AppTraceController.java`，GET / POST / DELETE `/apps/{alias}/trace` 共 3 endpoint，全部透传 region

## 8. 弹性伸缩规则 Controller + Service

- [x] 8.1 新建 `service/AutoscalerRuleService.java`，封装 createRule / updateRule / deleteRule / getRule / listByServiceId 共 5 method
- [x] 8.2 createRule：事务内 INSERT autoscaler_rules + autoscaler_rule_metrics → 调 AutoscalerOperations.createRule；region 失败回滚
- [x] 8.3 updateRule：事务内 UPDATE 本地 → 调 region；失败回滚
- [x] 8.4 deleteRule：先 region 再本地（保证 region 先释放 HPA CR）
- [x] 8.5 listByServiceId / getRule：仅本地查询，不调 region
- [x] 8.6 新建 `controller/AppAutoscalerController.java`，挂 5 endpoint：GET/POST `/xparules`、GET/PUT `/xparules/{rule_id}`、GET `/xparecords`
- [x] 8.7 xparecords 透传 AutoscalerOperations.listScalingRecords
- [x] 8.8 入参校验：min_replicas≥1、max_replicas∈[1,65535]、metrics 非空、metric_target_value∈[0,65535]

## 9. 批量 / 删除增强 Controller

- [x] 9.1 新建 `controller/AppBatchActionsController.java`，POST `/teams/{team_name}/batch_actions` —— 按 region_name 分组调 ServiceOperations.batchActions，部分失败时返回 success/failed 列表
- [x] 9.2 入参支持 `service_ids` 和 `service_alias_list` 两种字段，做并集解析
- [x] 9.3 新建 `controller/AppBatchDeleteController.java`，DELETE `/teams/{team_name}/batch_delete` 与 `/again_delete` 共 2 endpoint
- [x] 9.4 batch_delete：循环调 AppDeleteService.delete()；任一失败收集失败 id 列表
- [x] 9.5 again_delete：跳过 region 调用直接归档 + 清理本地表
- [x] 9.6 新建 `controller/AppGroupDeleteController.java`，DELETE `/teams/{team_name}/groupapp/{group_id}/delete` —— 列出 group 下 service 后走 batch_delete 链路 + 删除 service_group + service_group_relation

## 10. 配置注册与启动校验

- [x] 10.1 确认 `KuShipConsoleApplication.@SpringBootApplication` 自动扫描包含 `cn.kuship.console.modules.appruntime`（默认根包扫已覆盖）
- [x] 10.2 在 `infrastructure/region/api/RegionApiAutoConfiguration.java`（如不存在则创建）显式注册 MonitorOperations / AutoscalerOperations Impl 以避免 @Primary 冲突
- [x] 10.3 启动后跑 `mvn -pl kuship-console clean compile` 验证 0 编译错误

## 11. 集成测试

- [x] 11.1 新建 `test/.../appruntime/integration/AppLifecycleIntegrationTest.java` —— @MockBean ServiceOperations，断言 start/stop/restart 端点正确调用 mock 并 update_version+1
- [x] 11.2 新建 `AppScalingIntegrationTest.java` —— 真实 MySQL，断言 vertical 写入 min_cpu/min_memory，horizontal 写 min_node
- [x] 11.3 新建 `AppAutoscalerIntegrationTest.java` —— 真实 MySQL，断言 POST /xparules 写入 autoscaler_rules + autoscaler_rule_metrics 双表，且 region mock 被调用
- [x] 11.4 新建 `AppEventLogIntegrationTest.java` —— @MockBean ServiceOperations，断言 events / event_log / log_instance 端点透传响应字段（含 token / event_id / list）保留
- [x] 11.5 跑 `mvn -pl kuship-console test` 全部测试通过（4 新 + 7 老共 ≥85 用例）

## 12. 文档与对齐

- [x] 12.1 在 `kuship-console/CLAUDE.md` 模块结构段补齐 `appruntime/` 目录说明
- [x] 12.2 在 `infrastructure/region/api/README.md` 接口骨架进度表更新 ServiceOperations 7→22 / 新增 MonitorOperations / AutoscalerOperations 计数
- [ ] 12.3 在 `kuship-console/README.md` API 章节补齐 40 endpoint 列表（推迟：CLAUDE.md 已记录全部 controller 列表，README 留作 hardening）
- [x] 12.4 跑 `openspec validate migrate-console-app-runtime --strict` 通过
