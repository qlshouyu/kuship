## Why

`kuship-console/src/main/java/cn/kuship/console/modules/misc/kubeblocks/controller/KubeBlocksController.java` 在 `migrate-console-misc` 阶段以 8 个 stub endpoint 形态落地——`supportedDatabases` / `storageClasses` / `backupRepos` / `detail` / `backupConfig` / `backups` / `parameters` / `restore` 一律返回空 list 或 `{kubeblocks: false}` 占位响应。这是 kuship UI 数据库托管页面打开就空白、用户无法创建/查看/扩容 KubeBlocks 集群的根本原因。

`migrate-region-coverage-roadmap` 把 KubeBlocks 数据库托管列为 **P1 #1**（13 method、独立可起、UI 重度依赖），这是 P0 五项收尾后第一批被拉起的高价值业务子 change。kuship UI（`rainbond-ui` 派生）的 `data-center` / `kubeblocks-detail` / `db-cluster-create` / `backup-list` 等页面均通过本 change 的 13 个 endpoint 才能拿到真实数据。

清桩并落地 13 个 region method 不仅解决 stub 反响应问题，还释放三件后续工作：

1. UI 端 KubeBlocks 创建向导 / 集群详情页 / 备份恢复页 / 参数管理页可以直接对接 kuship-console，不再回退到 rainbond-console（7070）双跑
2. 本 change 在 `modules/misc/kubeblocks/api/` 落地 `KubeBlocksOperations` 业务自治接口，符合"非 14 核心接口放业务模块"的项目硬约束（路线图 design.md 决策 4），不污染 14 接口骨架
3. 后续 `add-kubeblocks-restore` / `add-kubeblocks-cluster-events` / `add-kubeblocks-connect-info` 等增量 hardening 可在同一 controller / Operations 接口上扩展，不重写 region URL 拼装

本 change 完整迁移 rainbond `regionapi.py:3347-3597` 13 段 region API 调用 + `console/views/kubeblocks.py:18-300` 8 个 view 的业务包装，URL 前缀统一 `/v2/cluster/kubeblocks/*`（region 端）。

## What Changes

### 实现 KubeBlocksOperations 接口（13 method）

新建 `modules/misc/kubeblocks/api/KubeBlocksOperations.java`（业务自治接口，非 14 核心 region 骨架），13 个 method 全部 1:1 透传 region API：

- `listClusters(regionName)` —— GET `/v2/cluster/kubeblocks/supported-databases`（rainbond 锚点 `regionapi.py:3347-3358 get_kubeblocks_supported_databases`）
- `listStorageClasses(regionName)` —— GET `/v2/cluster/kubeblocks/storage-classes`（`regionapi.py:3360-3371 get_kubeblocks_storage_classes`）
- `listBackupRepos(regionName)` —— GET `/v2/cluster/kubeblocks/backup-repos`（`regionapi.py:3373-3384 get_kubeblocks_backup_repos`）
- `getClusterDetail(regionName, serviceId)` —— GET `/v2/cluster/kubeblocks/clusters/{service_id}`（`regionapi.py:3413-3424 get_kubeblocks_cluster_detail`）
- `listClusterParameters(regionName, serviceId, page, pageSize, keyword)` —— GET `/v2/cluster/kubeblocks/clusters/{service_id}/parameters?page=&page_size=&keyword=`（`regionapi.py:3560-3584 get_kubeblocks_cluster_parameters`）
- `listClusterBackups(regionName, serviceId, page, pageSize)` —— GET `/v2/cluster/kubeblocks/clusters/{service_id}/backups?page=&page_size=`（`regionapi.py:3465-3486 get_kubeblocks_backup_list`）
- `getClusterPodDetail(regionName, serviceId, podName)` —— GET `/v2/cluster/kubeblocks/clusters/{service_id}/pods/{pod_name}/details`（`regionapi.py:3547-3558 kubeblocks_cluster_pod_detail`）
- `createCluster(regionName, body)` —— POST `/v2/cluster/kubeblocks/clusters`（`regionapi.py:3386-3397 create_kubeblocks_cluster`）
- `expansionCluster(regionName, serviceId, body)` —— PUT `/v2/cluster/kubeblocks/clusters/{service_id}`（`regionapi.py:3426-3437 expansion_kubeblocks_cluster`）
- `deleteCluster(regionName, body)` —— DELETE `/v2/cluster/kubeblocks/clusters`（with body）（`regionapi.py:3503-3514 delete_kubeblocks_cluster`）
- `deleteClusterBackups(regionName, serviceId, backups)` —— DELETE `/v2/cluster/kubeblocks/clusters/{service_id}/backups`（with body `{"backups":[...]}`）（`regionapi.py:3488-3501 delete_kubeblocks_backups`）
- `updateBackupConfig(regionName, serviceId, body)` —— PUT `/v2/cluster/kubeblocks/clusters/{service_id}/backup-schedules`（`regionapi.py:3439-3450 update_kubeblocks_backup_config`）
- `createManualBackup(regionName, serviceId)` —— POST `/v2/cluster/kubeblocks/clusters/{service_id}/backups`（无 body）（`regionapi.py:3452-3463 create_kubeblocks_manual_backup`）
- `updateClusterParameters(regionName, serviceId, body)` —— POST `/v2/cluster/kubeblocks/clusters/{service_id}/parameters`（`regionapi.py:3586-3597 update_kubeblocks_cluster_parameters`）

接口 method 数 = **13**（覆盖 8 个 GET 查询类 + 1 个 create + 1 个 expansion + 1 个 delete + 2 个 update + 1 个 manual-backup post + 1 个 parameter post 中的查询/写入混合，按 rainbond 13 段实际行数命中）。

### 落地 KubeBlocksOperationsImpl

新建 `modules/misc/kubeblocks/api/KubeBlocksOperationsImpl.java`，与接口同包，标记 `@Primary @Service`，覆盖默认 13 个 throw `UnsupportedOperationException` 桩。注入 `RegionClientFactory`、`tools.jackson.databind.ObjectMapper`、`RegionResponseProcessor`，全部走既有 region API helper（`processor.checkStatus / extractBean / extractList`）。

### 接线现有 8 个 controller endpoint（去 stub）

修改 `kuship-console/src/main/java/cn/kuship/console/modules/misc/kubeblocks/controller/KubeBlocksController.java`：

- 8 个现有 endpoint **路径不动**（继续匹配 rainbond `urls.py:1116-1130`），仅替换 stub 返回为对 `KubeBlocksOperations` 的真实调用
- 因为 rainbond 端 8 个 view 实际通过同一个 region 接口暴露多种 HTTP 方法（如 `KubeBlocksClusterDetailView` 同时处理 GET 详情 + PUT 扩容；`KubeBlocksClusterBackupListView` 处理 GET 列表 + POST 手动备份 + DELETE 删除备份；`KubeBlocksClusterParametersView` 处理 GET 列表 + POST 批量更新），controller 中需追加对应 PUT/POST/DELETE 注解的方法，最终 controller 对外暴露 **8 个 URL 路径 + 13 个 HTTP method 方法**（与 13 个 region method 一一对应）
- `restore` endpoint（rainbond `KubeBlocksClusterRestoreView`）涉及"从备份恢复 → 新建组件"复杂流程，**不在本 change 范围内**——保留现有 stub 返回 `{restore_started: true}`，由后续 `add-kubeblocks-restore` hardening change 独立落地（依赖 `migrate-console-app-create` 已落地的 `ServiceOperations.createService` 真实模板实例化）

### 不在本 change 内（明确推迟）

- KubeBlocks 集群事件/操作记录（`get_kubeblocks_cluster_events` GET `.../{service_id}/events`，rainbond `regionapi.py:3516-3528`）—— 推迟 `add-kubeblocks-cluster-events`
- 集群连接信息（`get_kubeblocks_connect_info` GET `.../clusters/connect-infos` 带 body）—— 推迟 `add-kubeblocks-connect-info`
- 批量集群状态管理（`manage_cluster_status` POST `.../clusters/actions`）—— 推迟 `add-kubeblocks-cluster-actions`
- 备份恢复创建组件（`restore_cluster_from_backup`）—— 推迟 `add-kubeblocks-restore`
- 创建/扩缩容前的 quota / 资源池校验—— 推迟 `add-kubeblocks-resource-validation`
- 本地 entity 持久化（KubeBlocks 集群状态 / 参数 / 备份）—— 本 change **不**落地任何本地表，13 method 全部走 region 实时

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "KubeBlocks 数据库托管透传"。覆盖 1 个新 controller（在原 `KubeBlocksController` 上接线，URL 不变）/ 8 endpoint × 13 HTTP method 的契约、`KubeBlocksOperations` 接口 13 个 method 的 region URL 路径与响应透传约束、与 `add-kubeblocks-*` hardening change（events / restore / actions / connect-info）的解耦边界。

## Impact

- **代码新增**：
  - region API：新增 1 个业务自治接口 `KubeBlocksOperations`（13 method）+ 默认实现 `KubeBlocksOperationsDefaultImpl`（throw unsupported）+ 真实实现 `KubeBlocksOperationsImpl`（`@Primary @Service`，13 method）
  - controller：在既有 `KubeBlocksController.java` 上扩展 5 个 HTTP method 注解（PUT detail / POST+DELETE backups / PUT backupConfig / POST parameters），删除 8 个 stub 返回值
  - entity：无新增（KubeBlocks 状态全走 region 实时；后续若需本地缓存或参数变更历史等，单独 hardening change 评估）
  - 单测：`KubeBlocksOperationsImplTest`（13 method 各覆盖 1 happy + 1 region 5xx 透传 = 26 用例），用 `MockRestServiceServer` 断言 URL/method/body shape
  - 集成测试：`KubeBlocksIntegrationTest`（`@SpringBootTest + @MockitoBean KubeBlocksOperations`），覆盖 13 method × 至少 6 个核心场景（cluster 列表 / 详情 / 创建 / 扩容 / 删除 / parameter 更新 / 备份配置更新 / 手动备份 / Pod 详情 / region 异常透传）
- **数据库**：无变更（不新增表 / 不动列 / Flyway 不增脚本）
- **依赖**：无新 Maven 依赖（沿用 RestClient + Jackson 3）
- **跨 change 衔接**：
  - 软依赖 `migrate-console-region-cluster`（提供 `RegionClientFactory` mTLS 客户端 + `region_info` entity）已落地，本 change 直接复用，零等待
  - 软依赖 `migrate-console-misc`（已落地 `KubeBlocksController` 8 个 stub 文件），本 change 在其基础上接线
  - 与 `migrate-console-app-create` 解耦：`add-kubeblocks-restore` hardening 时再衔接其 `ServiceOperations.createService`，本 change 不引入应用创建链路
- **不影响**：rainbond-console（仍可独立跑 7070）、其他已迁移 change（response advice / SecurityConfig / JWT 链路全部沿用）
- **路径变量**：8 个 endpoint 路径变量 `{team_name}` / `{region_name}` / `{service_alias}` 全部 snake_case 保留 rainbond Python 端命名（与项目硬约束一致；现有 stub 已经如此，本 change 不改）
- **URL 前缀**：region 端 13 个 URL 全部以 `/v2/kubeblocks/*`（rainbond Python 用 `/v2/cluster/kubeblocks/*`，本 change 沿用 rainbond 实际路径，proposal 中 "URL 前缀 /v2/kubeblocks/*" 为 design.md 路径表的简记，不修正 region Go 端路径）
- **鉴权**：8 个 endpoint 全部走默认 JWT 鉴权链 + `RequirePerm`/`RequireEnterpriseAdmin` 注解（详见 design.md 决策 5），无 permitAll 需求，本 change **不修改** SecurityConfig
