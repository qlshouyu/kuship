# Design — migrate-console-kubeblocks

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是 **P1 #1**（KubeBlocks 数据库托管），估计 method 数 **13**，工作量约 4-5 天，处于 ≤ 30 上限的中段。归档时反向更新母路线表对应行（`migrate-console-kubeblocks` 标记为已完成 + 写入实际工时）。

依赖：无（独立可起）。软依赖：

- `migrate-console-region-cluster`（已归档）—— 提供 `RegionClientFactory` mTLS 客户端 + `region_info` entity，复用既有 region API helper（`processor.checkStatus / extractBean / extractList`）
- `migrate-console-misc`（已归档）—— 提供 `KubeBlocksController` 8 个 stub controller 文件，本 change 在其基础上接线，避免重复创建 controller 类

后续 hardening：`add-kubeblocks-cluster-events` / `add-kubeblocks-restore` / `add-kubeblocks-cluster-actions` / `add-kubeblocks-connect-info` 都将在 `KubeBlocksOperations` 同一接口上扩展 method（不替换），URL 路径继续挂在 `KubeBlocksController` 上。

## Region API URL 表

| method 签名（Java） | HTTP | 路径 | rainbond 锚点 |
|---|---|---|---|
| `listSupportedDatabases(rn)` | GET | `/v2/cluster/kubeblocks/supported-databases` | `regionapi.py:3347-3358 get_kubeblocks_supported_databases` |
| `listStorageClasses(rn)` | GET | `/v2/cluster/kubeblocks/storage-classes` | `regionapi.py:3360-3371 get_kubeblocks_storage_classes` |
| `listBackupRepos(rn)` | GET | `/v2/cluster/kubeblocks/backup-repos` | `regionapi.py:3373-3384 get_kubeblocks_backup_repos` |
| `getClusterDetail(rn, sid)` | GET | `/v2/cluster/kubeblocks/clusters/{service_id}` | `regionapi.py:3413-3424 get_kubeblocks_cluster_detail` |
| `listClusterParameters(rn, sid, page, pageSize, keyword)` | GET | `/v2/cluster/kubeblocks/clusters/{service_id}/parameters?page=&page_size=&keyword=` | `regionapi.py:3560-3584 get_kubeblocks_cluster_parameters` |
| `listClusterBackups(rn, sid, page, pageSize)` | GET | `/v2/cluster/kubeblocks/clusters/{service_id}/backups?page=&page_size=` | `regionapi.py:3465-3486 get_kubeblocks_backup_list` |
| `getClusterPodDetail(rn, sid, podName)` | GET | `/v2/cluster/kubeblocks/clusters/{service_id}/pods/{pod_name}/details` | `regionapi.py:3547-3558 kubeblocks_cluster_pod_detail` |
| `createCluster(rn, body)` | POST | `/v2/cluster/kubeblocks/clusters` | `regionapi.py:3386-3397 create_kubeblocks_cluster` |
| `expansionCluster(rn, sid, body)` | PUT | `/v2/cluster/kubeblocks/clusters/{service_id}` | `regionapi.py:3426-3437 expansion_kubeblocks_cluster` |
| `deleteCluster(rn, body)` | DELETE | `/v2/cluster/kubeblocks/clusters` (with body `{service_ids:[...], delete_pvc, delete_backup}`) | `regionapi.py:3503-3514 delete_kubeblocks_cluster` |
| `deleteClusterBackups(rn, sid, backups)` | DELETE | `/v2/cluster/kubeblocks/clusters/{service_id}/backups` (with body `{backups:[...]}`) | `regionapi.py:3488-3501 delete_kubeblocks_backups` |
| `updateBackupConfig(rn, sid, body)` | PUT | `/v2/cluster/kubeblocks/clusters/{service_id}/backup-schedules` | `regionapi.py:3439-3450 update_kubeblocks_backup_config` |
| `createManualBackup(rn, sid)` | POST | `/v2/cluster/kubeblocks/clusters/{service_id}/backups` (无 body) | `regionapi.py:3452-3463 create_kubeblocks_manual_backup` |
| `updateClusterParameters(rn, sid, body)` | POST | `/v2/cluster/kubeblocks/clusters/{service_id}/parameters` | `regionapi.py:3586-3597 update_kubeblocks_cluster_parameters` |

method 总数 = 14 行表项中前 13 行为正式接口 method（rainbond 8 GET 查询类涵盖：listSupportedDatabases / listStorageClasses / listBackupRepos / getClusterDetail / listClusterParameters / listClusterBackups / getClusterPodDetail 共 7 GET method；任务说明的 "8 个 GET 查询类含 list_clusters / cluster_detail / list_addons / cluster_pods / get_kubeblocks_backups 等" 在 rainbond 实际只暴露 7 个独立 GET method，第 8 个 GET 是 `get_kubeblocks_connect_info`，由于其请求语义复杂（GET with body）已明确推迟到 hardening change `add-kubeblocks-connect-info`，本 change method 数 **13**）。

`createManualBackup` 与 `listClusterBackups` 共用 URL `/v2/cluster/kubeblocks/clusters/{service_id}/backups`（HTTP method 区分：POST vs GET），与 `deleteClusterBackups` 也共用同一 URL（DELETE with body）。三者对应同一 controller endpoint `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups`，由 controller 上 `@GetMapping/@PostMapping/@DeleteMapping` 注解分发。

`createCluster` 与 `deleteCluster` 共用 URL `/v2/cluster/kubeblocks/clusters`（POST vs DELETE）；rainbond 端 `delete_kubeblocks_cluster` 不带 path 中 service_id，是因为支持批量删除（body 中 `service_ids` 是数组）。

## Controller 路径锚点

| Controller method | 路径 | HTTP | rainbond 锚点 | 调用 Operations method |
|---|---|---|---|---|
| `supportedDatabases` | `/console/teams/{team_name}/regions/{region_name}/kubeblocks/supported_databases` | GET | `urls.py:1116 KubeBlocksAddonsView.get` | `listSupportedDatabases(rn)` |
| `storageClasses` | `/console/teams/{team_name}/regions/{region_name}/kubeblocks/storage_classes` | GET | `urls.py:1118 KubeBlocksStorageClassesView.get` | `listStorageClasses(rn)` |
| `backupRepos` | `/console/teams/{team_name}/regions/{region_name}/kubeblocks/backup_repos` | GET | `urls.py:1120 KubeBlocksBackupReposView.get` | `listBackupRepos(rn)` |
| `getDetail` | `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/detail` | GET | `urls.py:1122 KubeBlocksClusterDetailView.get` | `getClusterDetail(rn, sid)` |
| `expansion` | `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/detail` | PUT | `urls.py:1122 KubeBlocksClusterDetailView.put` | `expansionCluster(rn, sid, body)` |
| `getBackupConfig` | `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backup-config` | GET（**新增**） | rainbond 未暴露 GET（Python 仅 PUT），UI 需要查询当前配置才能编辑 | 通过 `getClusterDetail` 复用 + 取 detail 的 backup_config 字段（决策 6 详述） |
| `updateBackupConfig` | `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backup-config` | PUT | `urls.py:1124 KubeBlocksClusterBackupView.put` | `updateBackupConfig(rn, sid, body)` |
| `listBackups` | `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups` | GET | `urls.py:1126 KubeBlocksClusterBackupListView.get` | `listClusterBackups(rn, sid, page, pageSize)` |
| `manualBackup` | `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups` | POST | `urls.py:1126 KubeBlocksClusterBackupListView.post` | `createManualBackup(rn, sid)` |
| `deleteBackups` | `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/backups` | DELETE | `urls.py:1126 KubeBlocksClusterBackupListView.delete` | `deleteClusterBackups(rn, sid, backups)` |
| `listParameters` | `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/parameters` | GET | `urls.py:1128 KubeBlocksClusterParametersView.get` | `listClusterParameters(rn, sid, page, pageSize, keyword)` |
| `updateParameters` | `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/parameters` | POST | `urls.py:1128 KubeBlocksClusterParametersView.post` | `updateClusterParameters(rn, sid, body)` |
| `restore`（保留 stub） | `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/restore` | POST | `urls.py:1130 KubeBlocksClusterRestoreView.post` | **不调** Operations，stub 保持 `{restore_started: true}` 待 `add-kubeblocks-restore` 落地 |

trailing slash 兼容沿用既定规则（每 endpoint 同时声明 `path` 与 `path/`）；现有 stub controller 已正确写法，本 change 不改。

URL 路径段对照说明：

- rainbond URL 用 `supported_databases` / `storage_classes` / `backup_repos`（**snake_case，下划线**）；现有 kuship stub 已沿用，本 change 不改路径
- rainbond URL 用 `backup-config` / `restore`（**kebab-case 与裸词**），与上一行的 snake_case 不一致是 rainbond 历史拼写，**严格保留**不修复
- rainbond `urls.py:1130` 路径 `/restores`（带 s 复数），但现有 kuship stub controller 写为 `/restore`（无 s）—— 这是 stub 实现期遗漏，本 change **不动该路径**（stub 保持 `/restore`），由后续 `add-kubeblocks-restore` 决定是否对齐 rainbond 复数命名（见决策 7）

`getDetail` 与 `expansion` 共用同一 URL `/kubeblocks/detail`，对应 rainbond `KubeBlocksClusterDetailView` 的 GET + PUT 双分发——这是 rainbond Python 单 view 多方法的典型模式，kuship 控制层通过 `@GetMapping` + `@PutMapping` 同 path 实现。

## 决策 1 — KubeBlocksOperations 接口归属包路径

KubeBlocks 不属于 14 核心 region 接口骨架（`TenantOperations` / `ServiceOperations` / `ServiceVolumeOperations` / `ClusterOperations` 等位于 `infrastructure/region/api/`），按路线图 design.md 决策 4 共享规约："Operations 接口非 14 核心 → 放 `modules/<domain>/api/`"，本 change 接口落地在：

```
cn.kuship.console.modules.misc.kubeblocks.api.KubeBlocksOperations
cn.kuship.console.modules.misc.kubeblocks.api.KubeBlocksOperationsDefaultImpl  // 默认 throw UnsupportedOperationException
cn.kuship.console.modules.misc.kubeblocks.api.KubeBlocksOperationsImpl         // @Primary @Service 真实实现
```

与 `migrate-console-third-party-runtime` 的 `ThirdPartyServiceOperations`、`migrate-console-app-runtime` 的 `MonitorOperations` / `AutoscalerOperations` 同模式；不污染 14 接口骨架（不动 `infrastructure/region/api/` 目录）。

`@Primary` 注解保证 Spring 在多个 bean 候选时优先选择真实实现；默认 impl 用于：a) 单测时若不需要走 region 实际调用可注入默认；b) 接口签名变更时降低对调用方的破坏面。

## 决策 2 — KubeBlocksController 单 controller 多 HTTP method 接线

不拆分 `KubeBlocksClusterDetailController` / `KubeBlocksClusterBackupController` 等子 controller，13 个 HTTP method 全部接在既有 `KubeBlocksController.java` 上。理由：

1. rainbond Python 端就是一个 view 文件 + 一个 service 文件全部捏合，kuship 端拆分会偏离上游模型且增加跨 controller 协作负担
2. 现有 stub 已经在单 controller 内列出了 8 个 endpoint，去 stub 是"原地替换"成本最低
3. 13 method 公共依赖（`KubeBlocksOperations` + `TenantsRepository`（取 namespace 用，仅 `restore` hardening 阶段需要）+ `TenantServiceRepository`（取 service_id 用））都很轻，单 controller 注入合理
4. URL 路径前缀分两组（`/regions/{region_name}/...` 与 `/apps/{service_alias}/...`），不构成强解耦诉求

每个 endpoint 在 controller 中显式列出 `path` + `path/` 双声明（trailing slash 兼容）；stub 阶段已是该写法，本 change 接线时保留。

## 决策 3 — service_alias 解析 service_id 的归属层

rainbond Python 端 view 通过 `AppBaseView` 父类的 `self.service`（已注入 TenantService 实体）拿到 `service_id`：

```python
status_code, data = kubeblocks_service.get_cluster_detail(
    self.response_region,
    self.service.service_id  # ← 从 service_alias 路径变量解析后的 entity
)
```

kuship 端没有等价的"父类自动注入 service entity"机制，按既定 contract 在 controller 内显式做：

```java
@GetMapping(...)
public ApiResult getDetail(@PathVariable("team_name") String teamName,
                            @PathVariable("service_alias") String serviceAlias) {
    Tenants tenant = tenantsRepo.findByTenantName(teamName)
        .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    TenantService service = serviceRepo.findByTenantIdAndServiceAlias(tenant.getTenantId(), serviceAlias)
        .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    return kubeblocksOps.getClusterDetail(service.getServiceRegion(), service.getServiceId());
}
```

抽出公共 helper `KubeBlocksController.resolveService(teamName, serviceAlias)` 返 `TenantService`，11 个 `/apps/{service_alias}/kubeblocks/*` endpoint 共用。3 个 `/regions/{region_name}/kubeblocks/*` endpoint 不需要解析 service（参数直接用 `region_name` path 变量）。

## 决策 4 — 分页/keyword query 参数透传规则

`listClusterParameters(rn, sid, page, pageSize, keyword)` 与 `listClusterBackups(rn, sid, page, pageSize)` 接口签名要求透传 query string，规则：

- `page` / `pageSize`：缺省时从 controller 通过 `@RequestParam(required=false)` 接，**不传** `null` 给 Operations method（避免 URL 出现 `?page=null`），改传 default（`page=1`，`pageSize=10`，对齐 kuship-console 项目硬约束 `PageRequestAdapter` 一基分页规则）
- `keyword`：`@RequestParam(required=false, name="keyword") String keyword`，仅在非 null 且 `keyword.trim().length() > 0` 时拼入 URL；空白字符串视为不传
- URL 拼接顺序：`page=`、`page_size=`、`keyword=`（rainbond Python 实际是按入参顺序追加，本 change 沿用，不强制字典序）
- query value 全部 `URLEncoder.encode(value, UTF_8)` 处理，防止 `keyword` 含特殊字符（`&` / `=` / 中文）破坏 URL

`KubeBlocksOperationsImpl` 内部用 StringBuilder 拼接：

```java
StringBuilder qs = new StringBuilder();
qs.append("?page=").append(page);
qs.append("&page_size=").append(pageSize);
if (StringUtils.hasText(keyword)) {
    qs.append("&keyword=").append(URLEncoder.encode(keyword, UTF_8));
}
```

## 决策 5 — Controller 鉴权

按 rainbond 父类决定鉴权层级：

| Controller method | rainbond 父类 | kuship 注解 |
|---|---|---|
| `supportedDatabases` / `storageClasses` / `backupRepos` | `RegionTenantHeaderView` | `@RequirePerm(PermCode.TEAM_REGION_DESCRIBE)` |
| `getDetail` / `expansion` / `getBackupConfig` / `updateBackupConfig` / `listBackups` / `manualBackup` / `deleteBackups` / `listParameters` / `updateParameters` / `restore` | `AppBaseView` | `@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)`（读）/ `@RequirePerm(PermCode.APP_OVERVIEW_MANAGE)`（写：expansion / updateBackupConfig / deleteBackups / manualBackup / updateParameters / restore） |

读/写 PermCode 的具体码值需在实施期对齐项目 `PermCode` 枚举（沿用 `migrate-console-third-party-runtime` 已使用的 `APP_CREATE_PERMS` / `APP_OVERVIEW_DESCRIBE` 等，最终以 `cn.kuship.console.modules.account.constant.PermCode` 实际枚举为准；若枚举中无 `APP_OVERVIEW_MANAGE` 字面量，退化为 `APP_CREATE_PERMS`）。

本 change **不修改** SecurityConfig（13 endpoint 全部走默认 JWT 鉴权链 + RBAC 注解切面，无 permitAll 需求）。

## 决策 6 — `getBackupConfig` GET endpoint 处理

rainbond Python 端 `KubeBlocksClusterBackupView` 仅 PUT method（`urls.py:1124`），不暴露 GET。但 kuship-ui（rainbond-ui 派生）的备份配置编辑页需要先 GET 当前配置才能编辑后 PUT 提交。三种方案：

1. **沿用 rainbond，不加 GET**：UI 必须先调 `getDetail` 拿 `bean.backup_config` 字段
2. **本 change 加 GET endpoint**：UI 调 `GET /kubeblocks/backup-config` 直接拿配置
3. **推迟到独立 hardening**：本 change 严格只接线 rainbond 已暴露的 12 个 HTTP method，留给 `add-kubeblocks-backup-config-get` 再加

**决策**：选方案 1（沿用 rainbond）。理由：

- rainbond Python `kubeblocks_service.get_cluster_detail` 返回的 bean 已含 `backup_config` 字段，不冗余单设 GET
- 减少 region API 总调用数（detail 一次拿全部）
- 与"本 change 严格 1:1 透传 13 method"原则一致，不引入"超出 rainbond 行为"的新 endpoint

把现有 stub 的 `GET /kubeblocks/backup-config`（返 `{backup_enabled: false}`）**直接删除**（不接线 / 不留 stub）；UI 端改调 `GET /kubeblocks/detail`，从 `bean.backup_config` 取字段（kuship-ui 同步调整在前端 change，不在本 change 范围）。

## 决策 7 — `restore` endpoint 保留 stub

rainbond `KubeBlocksClusterRestoreView` 调用 `kubeblocks_service.restore_component_from_backup` —— 内部链路：

```
1. 校验 backup_name 存在
2. 创建新 service entity（全新 service_id / service_alias）
3. 调 region 创建集群 + 从备份恢复
4. 落 service_source 表
5. 落 service_group_relation 表
6. 调 region createService 真实模板实例化
```

本 change 不引入应用创建链路（避免与 `migrate-console-app-create` 强耦合，避免本 change 工作量超 ≤30 method 上限）。`restore` endpoint 保留现有 stub `return GeneralMessage.ok(Map.of("restore_started", true));`，独立 `add-kubeblocks-restore` hardening 落地。

stub 保留时打 INFO 日志：`[KubeBlocks][stub] restore endpoint hit; full restore flow pending add-kubeblocks-restore`，运维监控 `grep "KubeBlocks.*stub"` 可统计 prod 环境实际触达 restore 的频率。

## 决策 8 — region 异常透传 / 错误消息汉化

13 个 method 都是简单 region 透传，遇到 region 错误一律抛 `RegionApiException`，由 `GlobalExceptionHandler` 自动映射为 general_message 形状响应 + HTTP 状态码与业务码对齐（与 cluster-extras / third-party-runtime 一致）。

错误消息汉化优先用 region 自带 `msg_show`（rainbond Go 后端已汉化，如 `"集群创建失败: storage class not found"`），缺失时由 `RegionErrorMsgEnricher` 兜底处理常见短语（如 helm 接管冲突 / 资源池不足）。本 change 不为 KubeBlocks 单独引入新的 enricher 规则，复用既有规则即可。

特殊场景：

- `createCluster` body 校验失败 → region 端 400 + msg_show "数据库类型不支持"，透传
- `expansionCluster` 资源池不足 → region 端可能抛 `ClusterLackOfMemoryException`（412），由 `GlobalExceptionHandler` 已知映射规则处理
- `deleteClusterBackups` body 中 `backups` 数组为空 → region 端 400，透传（不在 controller 层做空数组校验，避免误差异）
- `getClusterDetail` 集群不存在 → region 端 404，透传 → controller 层不做"先查本地再调 region"的双查（KubeBlocks 状态全走 region 实时）

## 决策 9 — 测试用 region 端真实数据 vs Mock

`KubeBlocksOperationsImplTest` 走 `MockRestServiceServer`（与 `ClusterOperationsImplTest` / `ThirdPartyServiceOperationsImplTest` 同模式）：

- 13 method 各 1 happy + 1 region 5xx 透传 = **26 用例**
- 断言每个 method 的 URL / HTTP method / body shape（如 `deleteClusterBackups` 必须发 DELETE with body `{"backups":[...]}`）
- 不依赖本地起 region 容器（项目既定测试规约）

集成测试 `KubeBlocksIntegrationTest` 走 `@SpringBootTest + @ActiveProfiles({"local","contract-test"}) + @MockitoBean KubeBlocksOperations`：

- 至少 6 个核心场景：cluster 列表 / cluster 详情 / cluster 创建 / cluster 扩容 / cluster 删除 / parameter 更新 / 备份配置更新 / 手动备份 / Pod 详情 / region 异常透传（spec.md 至少 6 个 Scenario，本测试至少覆盖其中 6 个）
- ArgumentCaptor 断言 controller 传给 Operations 的入参形状（regionName / serviceId / body 透传）
- 契约形状断言 `code/msg/msg_show/data.bean/data.list` 五项（与项目硬约束一致）
- 至少 1 个 region 异常透传案例：mock `KubeBlocksOperations.getClusterDetail` 抛 `RegionApiException(503, ...)`，断言响应 503 + body 含 `msg_show`

## 非决策（明确不做）

- **不**改 13 个 method 之外的接口签名（如不引入 `getClusterEvents` / `getConnectInfo` / `manageClusterStatus` / `restoreFromBackup` 这 4 个推迟到 hardening 的 method 占位 default）
- **不**新建本地 entity（`kubeblocks_cluster` / `kubeblocks_backup` / `kubeblocks_parameter` 等表都不创建；KubeBlocks 状态全走 region 实时；后续若 UI 性能问题需要本地缓存，独立 hardening 评估）
- **不**做"先查本地再调 region"的双查或 fallback（与 cluster-extras 的 `getClusterInfo` 404 降级路径不同——后者有本地 `region_info` 表可降级，本 change 没有等价表）
- **不**做 quota / 资源池 pre-check（rainbond Python 也未做；UI 端校验 + region 端 412 错误透传足够）
- **不**改 `restore` endpoint stub 行为（保留占位，完整恢复流程留 `add-kubeblocks-restore`）
- **不**改 `restore` endpoint URL 路径单复数（rainbond `/restores` vs kuship stub `/restore`）—— 由 `add-kubeblocks-restore` 决策

## 测试约定

集成测试用 `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles({"local","contract-test"})` 启动完整应用上下文（与项目硬约束一致）；用 `@MockitoBean KubeBlocksOperations` 替换 Operations Impl，避免依赖真实 region。

集成测试覆盖（至少 6 用例）：

- `listSupportedDatabases` happy：mock 返 `Map<String,Object>` 含 `list`，断言响应 200 + general_message + `data.list` 字段
- `getClusterDetail` happy：mock 返 bean 含 `kubeblocks_status` / `backup_config` 字段，断言 `data.bean.kubeblocks_status` 透传
- `createCluster` happy：POST body 完整透传（字段不丢），verify Operations 入参 = controller 接收 body
- `expansionCluster` happy：PUT body 透传，verify region method 调用次数 = 1
- `deleteCluster` happy：DELETE with body，verify body shape `{service_ids:[...]}`
- `updateClusterParameters` happy：POST body 透传 + verify 调用 `updateClusterParameters` 而非 `listClusterParameters`
- `updateBackupConfig` happy：PUT body 透传
- `createManualBackup` happy：POST 无 body，verify Operations method 入参仅 `(rn, sid)`
- `getClusterPodDetail` happy：podName 含点号 / 中划线 / 数字
- `region 5xx 透传`：mock `getClusterDetail` 抛 `RegionApiException(503, "region service unavailable", "集群不可用")`，断言响应 503 + `data.bean.trace_id` + `msg_show=集群不可用`

每个核心场景至少 1 happy + 1 异常透传 + 1 contract shape 断言。

## 非决策清单回顾（聚合）

- 不新增 entity / 表
- 不修改 SecurityConfig
- 不改 14 接口骨架
- 不改 stub `restore` endpoint
- 不改 stub controller 路径单复数 / 拼写
- 不引入 KubeBlocks 集群事件 / connectInfo / batch actions / restore 4 个推迟 method
- 不做 quota pre-check

## 实施期探测结果（2026-05-10 落地）

- **PermCode 包路径修正**：design.md 决策 5 写为 `cn.kuship.console.modules.account.constant.PermCode`，实际项目枚举位于 `cn.kuship.console.modules.account.perm.PermCode`（且 PermCode 是 `String` 常量类，非 enum）。三个码值 `TEAM_REGION_DESCRIBE` / `APP_OVERVIEW_DESCRIBE` / `APP_CREATE_PERMS` 齐全；`APP_OVERVIEW_MANAGE` 不存在 → 写端点退化为 `APP_CREATE_PERMS`（与决策 5 末段一致）
- **业务自治接口签名规约**：新建 `KubeBlocksOperations` method 不带 `enterpriseId` 入参，内部调用 `clientFactory.getClient(regionName, "")` 用空串占位（与 `ThirdPartyServiceOperations` 同模式），避免污染 14 接口骨架的 `enterpriseId` 惯例
- **Region URL 探测推迟**：13 URL 联动验证留 task §8 用户本地起 console + region 后 curl 真实节点；本轮按 rainbond Python 锚点表落地实现，单测 + 集成测试 30 用例通过 MockRestServiceServer / @MockitoBean 无需在线 region
- **createCluster / deleteCluster 不暴露 controller endpoint**：按决策 4.8/4.9 实施，仅落地 `KubeBlocksOperations` method 供内部调用 + 单测覆盖；如确需 console URL 暴露由后续 `add-kubeblocks-create-flow` hardening 决定
- **测试结果**：`KubeBlocksOperationsImplTest` 18 用例 + `KubeBlocksIntegrationTest` 12 用例 = 30 用例全过；`mvn -DskipTests package` 编译通过
