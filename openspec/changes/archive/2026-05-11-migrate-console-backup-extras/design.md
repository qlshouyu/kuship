# Design — migrate-console-backup-extras

> **路线图位置**：母路线图 [`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/) §4.5（**P2 #5**，估计 5 method）
>
> rainbond 参照：
> - region API：`www/apiclient/regionapi.py:1685-1750`（7 个 backup 相关 region method）
> - service：`console/services/backup_service.py`（GroupAppBackupService 17 method）
> - urls：`console/urls/__init__.py:872-890`（10 个 backup/copy/migrate/delete URL）
> - views：`console/views/center_pool/groupapp_backup.py` + `groupapp_copy.py` + `groupapp_migration.py`

## 1. 范围与边界

### 1.1 已落地的（不动，仅修 URL）

kuship-console 已有 `cn.kuship.console.modules.appmarket.backup.*` 子树：
- `BackupOperations` 4 method：`backup` / `backupStatus` / `restore` / `export`
- `GroupAppBackupController` 6 endpoint：`startBackup` / `listStatus` / `export` / `importBackup` / `listTeamBackups` / `listAllEnterpriseBackups`
- entity：`ServiceGroupBackup`；repo：`ServiceGroupBackupRepository`

### 1.2 本 change 新增（5 region method + 4 controller endpoint）

| # | rainbond region method | kuship `BackupOperations` 新 method | rainbond URL | kuship controller endpoint |
|---|------------------------|-------------------------------------|--------------|----------------------------|
| 1 | `delete_backup_by_backup_id` | `deleteBackup(rn, tn, backupId)` | DELETE `/v2/tenants/{tn}/groupapp/backups/{backup_id}` | POST `/console/teams/{tn}/groupapp/{group_id}/delete` (rainbond 复用 GroupAppsView) |
| 2 | `get_backup_status_by_group_id` | `listBackupsByGroupUuid(rn, tn, groupUuid)` | GET `/v2/tenants/{tn}/groupapp/backups?group_id={uuid}` | （内部用，由现有 `listStatus` endpoint 调用，**优先 region 真相**取代本地 backupRepo 查询） |
| 3 | `star_apps_migrate_task` | `startMigrate(rn, tn, backupId, body)` | POST `/v2/tenants/{tn}/groupapp/backups/{backup_id}/restore` | POST `/console/teams/{tn}/groupapp/{group_id}/migrate` |
| 4 | `get_apps_migrate_status` | `getMigrateStatus(rn, tn, backupId, restoreId)` | GET `/v2/tenants/{tn}/groupapp/backups/{backup_id}/restore/{restore_id}` | GET `/console/teams/{tn}/groupapp/{group_id}/migrate/record?restore_id=` |
| 5 | `copy_backup_data` | `copyBackupData(rn, tn, body)` | POST `/v2/tenants/{tn}/groupapp/backupcopy` | POST `/console/teams/{tn}/groupapp/{group_id}/copy` |

### 1.3 既有 4 method URL 一致性修正

当前 `BackupOperationsImpl` 把 console URL 误用为 region URL，本 change 一并修正：

| method | 当前错误 URL | 修正为（rainbond 真相） |
|--------|---------------|--------------------------|
| `backup` | POST `/v2/tenants/{tn}/groupapp/{group_id}/backup` | POST `/v2/tenants/{tn}/groupapp/backups`（group_id 入 body）|
| `backupStatus` | GET `/v2/tenants/{tn}/groupapp/backup/{backup_id}` | GET `/v2/tenants/{tn}/groupapp/backups/{backup_id}` |
| `restore` | POST `/v2/tenants/{tn}/groupapp/backup/restore` | **删除**（与 `star_apps_migrate_task` 重叠，由新 `startMigrate` 取代；rainbond 不存在此 URL）|
| `export` | GET `/v2/tenants/{tn}/groupapp/backup/{backup_id}/export` | **删除**（rainbond region 不存在此 URL；export 是 console 内部 json 序列化逻辑，由 `BackupOperations` 之外的 `BackupExportService` 处理，本 change 不落地）|

**决策 1**：现有 controller `export` endpoint 保留路径但内部改为读 `ServiceGroupBackup` + `ServiceGroupBackupContent`（如有）+ AuthCode 加密 json 返回，**不调 region**。本 change 仅做"**摘除错误 region 调用**"的 stub 化，完整 export 逻辑留给独立 hardening。

**决策 2**：现有 controller `importBackup` endpoint 改调新的 `copyBackupData` region method，body 含 `event_id` / `group_id`(uuid) / `status` / `version` / `source_dir` / `source_type` / `backup_mode` / `backup_size` —— 与 rainbond `import_group_backup` 的 region body 一致。

## 2. 接口扩展

### 2.1 BackupOperations 新签名（既有 4 method 保留 + 5 新 method）

```java
public interface BackupOperations {
    // 既有 4 method（修正 URL）
    Map<String, Object> backup(String regionName, String tenantName, String groupId, Map<String, Object> body);
    Map<String, Object> backupStatus(String regionName, String tenantName, String backupId);

    /** @deprecated 由 startMigrate 取代；rainbond region 不存在此 URL */
    @Deprecated
    Map<String, Object> restore(String regionName, String tenantName, Map<String, Object> body);

    /** @deprecated console 内部 json 导出逻辑迁出此接口 */
    @Deprecated
    Map<String, Object> export(String regionName, String tenantName, String backupId);

    // 新增 5 method
    Map<String, Object> deleteBackup(String regionName, String tenantName, String backupId);
    List<Map<String, Object>> listBackupsByGroupUuid(String regionName, String tenantName, String groupUuid);
    Map<String, Object> startMigrate(String regionName, String tenantName, String backupId, Map<String, Object> body);
    Map<String, Object> getMigrateStatus(String regionName, String tenantName, String backupId, String restoreId);
    Map<String, Object> copyBackupData(String regionName, String tenantName, Map<String, Object> body);
}
```

### 2.2 实现要点（BackupOperationsImpl @Primary）

- 模式延续 region API client 套路：`clientFactory.getClient(rn, "")` + `RegionApiSupport.exchange(...)` + `processor.extractBean / checkStatus`
- DELETE method：`c.method(HttpMethod.DELETE).uri(url)`（无 body）
- query string：`listBackupsByGroupUuid` URL 拼 `?group_id={URLEncoder.encode(uuid)}`；返回 List 用 `processor.checkStatus(resp, ...)` 后从 `data.list` 提取
- migrate body 必须包含：`event_id`（console 生成 UUID）/ `group_id`（**target group uuid**，不是 source）/ `status` / `version` / `source_dir` / `source_type` / `backup_mode` / `backup_size`

## 3. Controller 扩展（GroupAppBackupController + 新 GroupAppsMigrateController）

考虑到既有 `GroupAppBackupController` / `GroupCopyMigrateController` 已存在，本 change 改造点：

### 3.1 现有 `GroupAppBackupController`（改造）

- **改造 `startBackup`**：调修正后的 `backupOps.backup(rn, tn, groupId, body)`；group_id 现在入 body 而非 URL
- **改造 `listStatus`**：从仅查本地 `backupRepo` 改为：先调 `listBackupsByGroupUuid` 拿 region 真相，再 merge 本地记录中 `note` / `mode` 等元数据；region 失败时 fallback 仅本地（容错）
- **改造 `export`**：摘除 `backupOps.export` 调用，改为本地 `ServiceGroupBackup` 序列化 json（决策 1）
- **改造 `importBackup`**：调新 `copyBackupData` region method（决策 2）

### 3.2 新增 `GroupAppsMigrateController`

新 controller，承接 rainbond `GroupAppsMigrateView` + `MigrateRecordView` + `GroupAppsView`：

| HTTP | URL | 说明 |
|------|-----|------|
| POST | `/console/teams/{team_name}/groupapp/{group_id}/migrate` | 启动迁移；body 校验 region / team / backup_id / migrate_type，调 `startMigrate` |
| GET | `/console/teams/{team_name}/groupapp/{group_id}/migrate/record?restore_id={id}` | 查询迁移状态，调 `getMigrateStatus` |
| POST | `/console/teams/{team_name}/groupapp/{group_id}/delete` | 删除组（rainbond 复用 `GroupAppsView`，含删 backup 记录），调 `deleteBackup` 后删除本地 `ServiceGroupBackup` |

### 3.3 新增 `GroupAppsCopyController`

承接 rainbond `GroupAppsCopyView`：

| HTTP | URL | 说明 |
|------|-----|------|
| POST | `/console/teams/{team_name}/groupapp/{group_id}/copy` | 跨集群复制；调 `copyBackupData`，body 含 source/target region |

## 4. 数据模型（仅复用，不新增表）

- `tenant_service_group_backup`（已落地）：`backup_id` / `group_id` / `event_id` / `status` / `note` / `mode` / `region` / `backup_size` / `team_id` / `user` / `create_time`
- `tenant_service_group_backup_record_migrate`：rainbond 中存在但路线图 P2 #5 范围内**不落地**（迁移记录由 region 后端持久化，console 仅透传查询）

**决策 3**：本 change SHALL NOT 新增 `migrate_record` 本地表；console 端的迁移状态查询完全透传 region（每次调 `getMigrateStatus`），不本地缓存。理由：迁移是一次性操作，状态短命；本地缓存增加同步复杂度但收益小。

## 5. 错误处理

- region 异常透传 `msg_show`，缺失才走 `RegionErrorMsgEnricher`
- `getMigrateStatus` 返回 404 时不抛异常，返回 `{"status": "not_found"}`（rainbond 行为兼容）
- `deleteBackup` region 返回 404 时（备份已不存在）SHALL 仍删除本地 `ServiceGroupBackup` 记录（最终一致性）

## 6. 测试

- `BackupOperationsImplTest`（单测，MockRestServiceServer）：5 个新 method × 1 happy + 1 错误 = ~10 用例；4 既有 method URL 修正回归 4 用例
- `BackupExtrasIntegrationTest`（@SpringBootTest + JdbcTemplate seed）：4 个新 controller endpoint × happy/error = 8 用例
- 共 ~22 用例

## 7. 实施期决策（占位段，apply 阶段回填）

待 apply 阶段补：
- region URL 真实路径与 design 假设的一致性（如 `?group_id=` 而非 `path` 段）
- migrate body 字段集（rainbond 历史可能有 `team_id` / `user` 等额外字段）
- 既有 contract-test 是否会因 URL 修正破跑（如有，修测试期望或加配置阀）
