## ADDED Requirements

### Requirement: KubeBlocks 数据库托管透传

kuship-console SHALL 把 `KubeBlocksController` 中既存的 8 个 stub endpoint（`supportedDatabases` / `storageClasses` / `backupRepos` / `detail` / `backupConfig` / `backups` / `parameters` / `restore`）替换为对 region API 的真实透传调用，同时落地新接口 `cn.kuship.console.modules.misc.kubeblocks.api.KubeBlocksOperations`（13 method，业务自治接口，非 14 核心 region 骨架）+ 真实实现 `KubeBlocksOperationsImpl`（`@Primary @Service`）。本 Requirement 同时锁定与后续 `add-kubeblocks-restore` / `add-kubeblocks-cluster-events` / `add-kubeblocks-cluster-actions` / `add-kubeblocks-connect-info` 4 个 hardening change 的解耦边界：本 change 仅做 region 透传，不做应用创建链路 / 集群事件查询 / 批量集群操作 / 连接信息（GET with body）；后续 hardening SHALL 在 `KubeBlocksOperations` 上扩展 method（不替换签名），URL 路径继续挂在 `KubeBlocksController` 上。`restore` endpoint 保留 stub `{restore_started: true}` 响应，由 `add-kubeblocks-restore` 单独落地。

业务规则：

- `KubeBlocksOperations` 接口包路径 MUST 是 `cn.kuship.console.modules.misc.kubeblocks.api`（业务自治，非 `infrastructure/region/api/` 14 核心骨架）；接口 + 默认 impl + `@Primary @Service` 真实 impl 三件套与 `ThirdPartyServiceOperations` / `MonitorOperations` / `AutoscalerOperations` 同模式
- `KubeBlocksOperations.listClusterParameters(rn, sid, page, pageSize, keyword)` MUST 在 `keyword` 为 null 或全空白字符时不拼入 query string，仅在非空时按 `&keyword=<URL-encode>` 拼接；`page` / `pageSize` 缺省时 controller 层填 `page=1` / `pageSize=10`，不传 null 给 Operations method
- `KubeBlocksOperations.deleteClusterBackups(rn, sid, backups)` MUST 发 DELETE 请求且 body 形状为 `{"backups": [<string array>]}`，与 rainbond `regionapi.py:3488-3501 delete_kubeblocks_backups` 一致
- `KubeBlocksOperations.deleteCluster(rn, body)` MUST 发 DELETE 请求且 body 透传 controller 接收的完整 Map（含 `service_ids` 数组等），不在 impl 层强 typed
- `KubeBlocksOperations.createManualBackup(rn, sid)` MUST 发 POST 请求**无 body**（rainbond Python `_post(url, headers, region=)` 不传 body 参数对齐）
- `KubeBlocksOperations.getClusterPodDetail(rn, sid, podName)` MUST 对 `podName` 做 URL encode，确保节点名含 `.` / `-` / 数字时路径不被错误解析（与 cluster-extras 对节点名的处理一致）
- 所有 `/apps/{service_alias}/kubeblocks/*` endpoint MUST 在 controller 层先解析 `team_name` → `Tenants` entity（404 返 `团队不存在`）→ 再用 `tenant.tenantId` + `service_alias` 查 `TenantService` entity（404 返 `组件不存在`），从 service entity 拿 `serviceRegion` + `serviceId` 传给 Operations method
- 所有 `/regions/{region_name}/kubeblocks/*` endpoint MUST 直接用 path 中的 `region_name` 调 Operations，不需要解析 service entity
- 13 endpoint 全部走默认 JWT 鉴权链 + RBAC 注解（读端点 `@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)` 或 `@RequirePerm(PermCode.TEAM_REGION_DESCRIBE)`；写端点 `@RequirePerm(PermCode.APP_CREATE_PERMS)`），不进 SecurityConfig 的 permitAll 白名单
- region 异常 MUST 透传 `RegionApiException`，由 `GlobalExceptionHandler` 自动映射为 general_message 形状响应（`code/msg/msg_show/data.bean/data.list` 五项）+ HTTP 状态码与业务码对齐；错误消息汉化优先用 region 自带 `msg_show`，缺失时由既有 `RegionErrorMsgEnricher` 兜底
- 本 change MUST NOT 落地任何本地 entity / 表（KubeBlocks 状态全走 region 实时）；MUST NOT 修改 SecurityConfig；MUST NOT 修改 14 接口骨架
- `restore` endpoint 行为：保留现有 stub 返 `{restore_started: true}` + 打 INFO 日志 `[KubeBlocks][stub] restore endpoint hit; full restore flow pending add-kubeblocks-restore`，由 `add-kubeblocks-restore` hardening 接 `ServiceOperations.createService` 真实模板实例化链路
- `getBackupConfig` GET endpoint 在本 change 范围内**删除** stub（rainbond Python 端原本不暴露 GET）；UI 改调 `getDetail` 取 `bean.backup_config` 字段；`PUT /kubeblocks/backup-config` 仍接线 `updateBackupConfig` Operations method

#### Scenario: KubeBlocks 支持的数据库类型查询

- **GIVEN** team `default` 有效，region_name=`rainbond`
- **WHEN** `GET /console/teams/default/regions/rainbond/kubeblocks/supported_databases`
- **THEN** kuship 调 region `GET /v2/cluster/kubeblocks/supported-databases`
- **AND** 响应 200 + `data.list` 含支持的数据库类型透传 region（如 `[{"name":"mysql","versions":["8.0","5.7"]},{"name":"postgresql","versions":["14"]}]`）
- **AND** 响应 shape 含 `code/msg/msg_show/data.bean/data.list` 五项

#### Scenario: KubeBlocks 集群详情查询

- **GIVEN** team `default`，service_alias `db-mysql-001` 已存在且 `service.serviceRegion="rainbond"`，`service.serviceId="abcd1234"`
- **WHEN** `GET /console/teams/default/apps/db-mysql-001/kubeblocks/detail`
- **THEN** kuship 调 region `GET /v2/cluster/kubeblocks/clusters/abcd1234`
- **AND** 响应 200 + `data.bean` 含 `kubeblocks_status` / `backup_config` / `replicas` / `version` 等字段（透传 region）

#### Scenario: KubeBlocks 集群扩容

- **GIVEN** team `default`，service_alias `db-mysql-001`，body `{"replicas":3,"cpu":"1000m","memory":"2Gi"}`
- **WHEN** `PUT /console/teams/default/apps/db-mysql-001/kubeblocks/detail`
- **THEN** kuship 调 region `PUT /v2/cluster/kubeblocks/clusters/abcd1234`，body 完整透传 3 个字段
- **AND** 响应 200 + `data.bean` 含 region 返回的 scale result

#### Scenario: KubeBlocks 集群参数批量更新

- **GIVEN** team `default`，service_alias `db-mysql-001`，body `{"parameters":[{"name":"innodb_buffer_pool_size","value":"1G"},{"name":"max_connections","value":"500"}]}`
- **WHEN** `POST /console/teams/default/apps/db-mysql-001/kubeblocks/parameters`
- **THEN** kuship 调 region `POST /v2/cluster/kubeblocks/clusters/abcd1234/parameters`，body 完整透传
- **AND** 响应 200 + `data.bean` 透传 region update result

#### Scenario: KubeBlocks 备份配置更新

- **GIVEN** team `default`，service_alias `db-mysql-001`，body `{"enabled":true,"cron":"0 2 * * *","retention_period":"7d"}`
- **WHEN** `PUT /console/teams/default/apps/db-mysql-001/kubeblocks/backup-config`
- **THEN** kuship 调 region `PUT /v2/cluster/kubeblocks/clusters/abcd1234/backup-schedules`，body 完整透传
- **AND** 响应 200 + `data.bean` 透传 region 配置 result

#### Scenario: KubeBlocks 手动备份触发

- **WHEN** `POST /console/teams/default/apps/db-mysql-001/kubeblocks/backups`（无 body）
- **THEN** kuship 调 region `POST /v2/cluster/kubeblocks/clusters/abcd1234/backups` 不带 body
- **AND** 响应 200 + `data.bean` 透传 region 手动备份启动 result

#### Scenario: KubeBlocks 备份列表分页查询带 keyword

- **WHEN** `GET /console/teams/default/apps/db-mysql-001/kubeblocks/parameters?page=2&page_size=20&keyword=innodb`
- **THEN** kuship 调 region `GET /v2/cluster/kubeblocks/clusters/abcd1234/parameters?page=2&page_size=20&keyword=innodb`
- **AND** keyword `innodb` URL encoded 后透传到 region URL（无空白字符 / 中文也不丢）
- **AND** 响应 200 + `data.list` 透传 region 参数列表

#### Scenario: KubeBlocks 集群备份批量删除

- **GIVEN** team `default`，service_alias `db-mysql-001`，body `{"backups":["backup-2026-05-08","backup-2026-05-09"]}`
- **WHEN** `DELETE /console/teams/default/apps/db-mysql-001/kubeblocks/backups`
- **THEN** kuship 调 region `DELETE /v2/cluster/kubeblocks/clusters/abcd1234/backups` body 形状为 `{"backups":["backup-2026-05-08","backup-2026-05-09"]}`
- **AND** 响应 200 + `data.list` 透传被删除的备份名称列表

#### Scenario: KubeBlocks 集群 Pod 详情含点号

- **GIVEN** team `default`，service_alias `db-mysql-001`，pod_name `mysql-cluster-0.example.com`
- **WHEN** `GET /console/teams/default/apps/db-mysql-001/kubeblocks/pods/mysql-cluster-0.example.com/details`（如本 change 决定暴露此 endpoint；若本 change 未暴露 controller endpoint，则 Operations method 单测覆盖此场景）
- **THEN** kuship 调 region `GET /v2/cluster/kubeblocks/clusters/abcd1234/pods/mysql-cluster-0.example.com/details`，podName 中的 `.` 字符 URL encode 后不丢失
- **AND** 响应 200 + `data.bean` 透传 Pod 详情

#### Scenario: 团队不存在的 KubeBlocks 调用

- **WHEN** `GET /console/teams/no-such-team/apps/db-mysql-001/kubeblocks/detail`
- **THEN** 响应 404 + `msg_show=团队不存在`
- **AND** 未发起任何 region 调用（verify `KubeBlocksOperations` 0 次调用）

#### Scenario: 组件不存在的 KubeBlocks 调用

- **GIVEN** team `default` 存在，但 service_alias `no-such-svc` 不存在
- **WHEN** `GET /console/teams/default/apps/no-such-svc/kubeblocks/detail`
- **THEN** 响应 404 + `msg_show=组件不存在`
- **AND** 未发起任何 region 调用

#### Scenario: region 5xx 透传

- **GIVEN** region 端 `/v2/cluster/kubeblocks/clusters/abcd1234` 返 503 + msg_show "集群服务暂不可用"
- **WHEN** `GET /console/teams/default/apps/db-mysql-001/kubeblocks/detail`
- **THEN** 响应 503 + general_message 形状（`code/msg/msg_show/data.bean/data.list` 五项），HTTP 状态码等于业务 code
- **AND** `msg_show` 来自 region 自带的中文消息（`集群服务暂不可用`，由 `GlobalExceptionHandler` 透传 `RegionApiException.msgShow`）
- **AND** 响应 `data.bean.trace_id` 字段非空，便于用户复制后报障

#### Scenario: restore endpoint 保留 stub

- **GIVEN** 本 change 已落地
- **WHEN** `POST /console/teams/default/apps/db-mysql-001/kubeblocks/restore` body=`{"backup_name":"backup-2026-05-08"}`
- **THEN** 响应 200 + `data.bean.restore_started=true`（保留 stub 行为，不调真实 region API）
- **AND** 服务端日志含 INFO 级 `[KubeBlocks][stub] restore endpoint hit; full restore flow pending add-kubeblocks-restore`
- **AND** 后续 `add-kubeblocks-restore` hardening change 落地后，此 endpoint 行为变更为接 `ServiceOperations.createService` 真实模板实例化 + 调 region restore method

#### Scenario: 与 add-kubeblocks-* hardening 的边界

- **GIVEN** 本 change 已落地，`KubeBlocksOperations` 含 13 method
- **WHEN** `add-kubeblocks-cluster-events` hardening 要加 `getClusterEvents(rn, sid, page, pageSize)` method（GET `/v2/cluster/kubeblocks/clusters/{sid}/events`）
- **THEN** 该 hardening change SHALL 在 `KubeBlocksOperations` 接口上**追加** method（不替换 / 不重命名既有 13 method），controller 层在 `KubeBlocksController` 上**追加** `@GetMapping("/console/teams/{tn}/apps/{sa}/kubeblocks/events")`，URL 与既有 endpoint 不冲突
- **AND** 同理 `add-kubeblocks-restore` / `add-kubeblocks-cluster-actions` / `add-kubeblocks-connect-info` 都遵循"接口追加 method + controller 追加 endpoint" 规则，不动既有 13 method 行为
- **AND** 业务级聚合（如 KubeBlocks 集群本地状态缓存 / 参数变更历史 / 批量操作事务）SHALL 通过新接口或新 service class 承载，不污染 `KubeBlocksOperations` 透传层
