# kuship-console-app

## ADDED Requirements

### Requirement: 整组应用备份扩展 region 调用（migrate-console-backup-extras）

kuship-console 后端 SHALL 在既有 `BackupOperations` 4 method 基础上扩展 5 个 region method（`deleteBackup` / `listBackupsByGroupUuid` / `startMigrate` / `getMigrateStatus` / `copyBackupData`），覆盖 rainbond `services/backup_service.py` + `regionapi.py:1685-1750` 中迁移 / 复制 / 单条删除 / 按组列出 4 类未迁移能力，并修正既有 4 method 的 region URL 与 rainbond 真实路径对齐。

本 Requirement 是母路线图 [`migrate-region-coverage-roadmap`](../../../migrate-region-coverage-roadmap/) 表中 **P2 #5** 行的细化契约。

#### Scenario: 删除单条备份记录

- **WHEN** 客户端调 POST `/console/teams/{team_name}/groupapp/{group_id}/delete` 且 body 含 `backup_id`
- **THEN** 后端 SHALL 调 region DELETE `/v2/tenants/{tenant_name}/groupapp/backups/{backup_id}`
- **AND** SHALL 删除本地 `tenant_service_group_backup` 中对应 `backup_id` 行
- **AND** region 返回 404 时仍删除本地行（最终一致性兜底）
- **AND** 响应 200 + `{"code": 200, "msg": "success"}`

#### Scenario: 按 group uuid 列出 region 端真实备份状态

- **WHEN** 客户端调 GET `/console/teams/{team_name}/groupapp/{group_id}/backup/all_status`
- **THEN** 后端 SHALL 先调 region GET `/v2/tenants/{tenant_name}/groupapp/backups?group_id={group_uuid}` 拿真相
- **AND** SHALL 用本地 `tenant_service_group_backup` 的 `note` / `mode` 字段对返回 list 做 merge
- **AND** region 调用失败时 SHALL fallback 为仅返回本地记录（不抛异常）

#### Scenario: 启动跨集群迁移恢复任务

- **WHEN** 客户端调 POST `/console/teams/{team_name}/groupapp/{group_id}/migrate` 且 body 含 `region` / `team` / `backup_id` / `migrate_type`
- **THEN** 后端 SHALL 校验 target team 存在 + target region 已开通团队权限
- **AND** SHALL 生成 `event_id`（UUID）+ target `group_uuid`（UUID）
- **AND** SHALL 调 region POST `/v2/tenants/{tenant_name}/groupapp/backups/{backup_id}/restore` body 含 `event_id` / `group_id`(target uuid) / `status` / `version` / `source_dir` / `source_type` / `backup_mode` / `backup_size`
- **AND** 响应 200 + `bean` 含 region 返回的 `restore_id`

#### Scenario: 查询迁移恢复任务状态

- **WHEN** 客户端调 GET `/console/teams/{team_name}/groupapp/{group_id}/migrate/record?restore_id={id}`
- **THEN** 后端 SHALL 调 region GET `/v2/tenants/{tenant_name}/groupapp/backups/{backup_id}/restore/{restore_id}`
- **AND** 当 `restore_id` 缺失时 SHALL 返回 400 `"请指明查询的备份ID"`
- **AND** region 返回 404 时 SHALL 返回 200 `bean = {"status": "not_found"}`（rainbond 行为兼容）

#### Scenario: 跨集群复制备份数据

- **WHEN** 客户端调 POST `/console/teams/{team_name}/groupapp/{group_id}/copy` 或 import 端点
- **THEN** 后端 SHALL 调 region POST `/v2/tenants/{tenant_name}/groupapp/backupcopy` body 含 source/target region 字段 + 备份元数据
- **AND** 响应 200 + region 返回的 backup 元数据 bean

#### Scenario: 现有 backup region URL 与 rainbond 对齐

- **WHEN** 后端调用 `BackupOperations.backup(rn, tn, groupId, body)` 创建备份
- **THEN** SHALL 走 region POST `/v2/tenants/{tenant_name}/groupapp/backups`（**复数 + group_id 入 body**），不再走 `/groupapp/{group_id}/backup`
- **WHEN** 后端调用 `BackupOperations.backupStatus(rn, tn, backupId)`
- **THEN** SHALL 走 region GET `/v2/tenants/{tenant_name}/groupapp/backups/{backup_id}`（**复数**），不再走 `/groupapp/backup/{backup_id}`

#### Scenario: 弃用 method 不再调 region

- **WHEN** 老调用方调 `BackupOperations.restore(...)` 或 `BackupOperations.export(...)`
- **THEN** 实现 SHALL 抛 `UnsupportedOperationException("deprecated; use startMigrate / local export")`
- **AND** controller 层 `export` endpoint SHALL 改为读本地 `ServiceGroupBackup` 序列化 json 返回，不调 region

#### Scenario: 路线图位置可追溯

- **WHEN** 团队成员看到本 Requirement
- **THEN** SHALL 在 `kuship-console/CLAUDE.md` "Region API 覆盖度路线" 表 P2 #5 行 + 本 spec 文件头部找到完整路线图引用
- **AND** SHALL 不与其它 P0/P1/P2 子 change 的 region URL 前缀重叠（本 change 唯一前缀：`/v2/tenants/{tn}/groupapp/backups*` + `/v2/tenants/{tn}/groupapp/backupcopy`）
