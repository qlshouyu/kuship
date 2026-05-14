# Tasks — migrate-console-backup-extras

> **路线图**：母 [`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/) §4.5（P2 #5，5 method 估计）

## 1. 接口扩展

- [x] 1.1 在 `BackupOperations` 既有 4 method 之后追加 5 个新签名（`deleteBackup` / `listBackupsByGroupUuid` / `startMigrate` / `getMigrateStatus` / `copyBackupData`）
- [x] 1.2 把既有 `restore` / `export` 标 `@Deprecated` 注释；保留方法签名以避免破坏调用方（实际逻辑改为 stub 或本地化）
- [x] 1.3 修正既有 `backup` / `backupStatus` 的 region URL 注释，预告 1.4-1.5 的 Impl 修正

## 2. Impl 实现（BackupOperationsImpl @Primary）

- [x] 2.1 修正 `backup` URL 为 `/v2/tenants/{tn}/groupapp/backups`（group_id 入 body）
- [x] 2.2 修正 `backupStatus` URL 为 `/v2/tenants/{tn}/groupapp/backups/{backup_id}`
- [x] 2.3 `restore` 改为内部 stub（log warn + throw `UnsupportedOperationException`）；`export` 同 stub
- [x] 2.4 实现 `deleteBackup`（DELETE）
- [x] 2.5 实现 `listBackupsByGroupUuid`（GET + query string + 返回 List）
- [x] 2.6 实现 `startMigrate`（POST + body）
- [x] 2.7 实现 `getMigrateStatus`（GET，404 兼容返回 `not_found` 而非异常）
- [x] 2.8 实现 `copyBackupData`（POST + body）

## 3. Controller 改造与新增

### 3.1 改造 `GroupAppBackupController`

- [x] 3.1.1 `startBackup`：group_id 入 body（与 rainbond 对齐），调用点不变
- [x] 3.1.2 `listStatus`：先调 `listBackupsByGroupUuid` region 真相，再 merge 本地 note/mode；region 失败 fallback 仅本地
- [x] 3.1.3 `export`：摘除 `backupOps.export` 调用，改为本地 `ServiceGroupBackup` 序列化 + AuthCode 加密 json 返回（如 AuthCode 实现复杂可暂留 TODO，仅返回 plain json）
- [x] 3.1.4 `importBackup`：调 `copyBackupData` 替换原 `restore`；body 含 event_id/group_id(uuid)/status/version/source_dir/source_type/backup_mode/backup_size

### 3.2 新增 `GroupAppsMigrateController`

- [x] 3.2.1 创建 controller 类骨架，注入 `BackupOperations + ServiceGroupBackupRepository + TenantsRepository + ServiceGroupRepository`
- [x] 3.2.2 POST `/console/teams/{team_name}/groupapp/{group_id}/migrate`：校验 region / team / backup_id / migrate_type，调 `startMigrate`
- [x] 3.2.3 GET `/console/teams/{team_name}/groupapp/{group_id}/migrate/record?restore_id={id}`：调 `getMigrateStatus`
- [x] 3.2.4 POST `/console/teams/{team_name}/groupapp/{group_id}/delete`：调 `deleteBackup` 后删除本地 `ServiceGroupBackup`；404 兼容仍删本地

### 3.3 新增 `GroupAppsCopyController`

- [x] 3.3.1 创建 controller 类
- [x] 3.3.2 POST `/console/teams/{team_name}/groupapp/{group_id}/copy`：调 `copyBackupData`

## 4. 单元测试（BackupOperationsImplTest）

- [x] 4.1 `deleteBackup_happy` + `deleteBackup_404_swallow`
- [x] 4.2 `listBackupsByGroupUuid_happy` + `listBackupsByGroupUuid_empty`
- [x] 4.3 `startMigrate_happy` + `startMigrate_500`
- [x] 4.4 `getMigrateStatus_happy` + `getMigrateStatus_404_returns_not_found`
- [x] 4.5 `copyBackupData_happy`
- [x] 4.6 既有 4 method URL 修正后回归（`backup_url_correct` / `backupStatus_url_correct` / `restore_deprecated_throws` / `export_deprecated_throws`）

## 5. 集成测试（BackupExtrasIntegrationTest）

- [x] 5.1 POST migrate happy + 团队不存在 / 集群不可用 错误分支
- [x] 5.2 GET migrate/record happy + restore_id 缺失 400
- [x] 5.3 POST groupapp/{id}/delete happy（含本地 backup 记录删除）+ 404 兼容
- [x] 5.4 POST groupapp/{id}/copy happy

## 6. 验证与归档

- [x] 6.1 `mvn -DskipTests package` 编译通过
- [x] 6.2 `mvn test -Dtest=BackupOperationsImplTest,BackupExtrasIntegrationTest` 全过
- [x] 6.3 既有 `GroupAppBackupController` 集成测试零回归（如有）
- [x] 6.4 联动验证（用户本地起 console + region + curl）：5 个新 region URL 真实可调
- [x] 6.5 实施期探测结果回填到 design.md §7
- [x] 6.6 母路线图 §4.5 标 [x] + 加 4.5.3 实施落地条目
- [x] 6.7 `kuship-console/CLAUDE.md` 表格 P2 #5 状态从 ⏳ 改为 ✅，line 锚点指向新 controller 类
- [x] 6.8 `openspec archive migrate-console-backup-extras --skip-specs`
