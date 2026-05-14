# Proposal — migrate-console-backup-extras

## Why

kuship-console 已落地基础整组备份（`BackupOperations` 4 method + `GroupAppBackupController` 6 endpoint），但与 rainbond `services/backup_service.py` + `regionapi.py` 比对后存在 5 段未迁移的 region 调用，并且现有 4 method 的 region URL 与 rainbond 真实路径不一致：

**5 段未迁移 region method**（路线图 P2 #5 范围）：

| # | rainbond region method | URL | 用途 |
|---|------------------------|-----|------|
| 1 | `delete_backup_by_backup_id` | DELETE `/v2/tenants/{tn}/groupapp/backups/{backup_id}` | 删除单条备份记录 |
| 2 | `get_backup_status_by_group_id` | GET `/v2/tenants/{tn}/groupapp/backups?group_id=` | 列出某 group 的所有备份 |
| 3 | `star_apps_migrate_task` | POST `/v2/tenants/{tn}/groupapp/backups/{backup_id}/restore` | 启动迁移恢复任务 |
| 4 | `get_apps_migrate_status` | GET `/v2/tenants/{tn}/groupapp/backups/{backup_id}/restore/{restore_id}` | 查询迁移恢复状态 |
| 5 | `copy_backup_data` | POST `/v2/tenants/{tn}/groupapp/backupcopy` | 跨集群复制备份数据（import） |

**现有 4 method URL 一致性修正**（边界处理）：当前 `BackupOperationsImpl` 把 console controller URL 当 region URL 用，例如 `backup` 调 `/v2/tenants/{tn}/groupapp/{group_id}/backup`（错），实际 rainbond region 是 `/v2/tenants/{tn}/groupapp/backups`（复数 + group_id 入 body）。本 change 一并修正。

## What Changes

- **接口扩展**：`BackupOperations` 既有 4 method 之后追加 5 个新 method（delete / statusByGroup / startMigrate / migrateStatus / copyBackupData），并修正既有 4 method 的 region URL 与 rainbond 对齐
- **Impl 扩展**：`BackupOperationsImpl` `@Primary` 落地 5 个新 method 的实现
- **Controller 扩展**：`GroupAppBackupController` 在既有 6 endpoint 之后追加 4 个 endpoint（POST migrate / GET migrate/record / POST groupapp/{id}/delete / DELETE backup_id）；按 rainbond `urls/__init__.py:884-890` 的契约
- **路径修正**：现有 `backup` / `backupStatus` / `restore` / `export` method 的 region URL 修正为 rainbond 真实路径
- **测试**：5 个新 method 单测 + 4 个新 controller endpoint 集成测试

## Impact

- **能力**：`kuship-console-app`
- **Specs**：ADDED 5 段 Requirement（每段一个 region method 端到端契约）+ MODIFIED 既有 backup 4 个 method 的 URL 契约
- **影响范围**：仅 `cn.kuship.console.modules.appmarket.backup.*`；不动 14 接口骨架；不动其它 module
- **不实现**：本 change SHALL NOT 处理 backup 文件的实际跨集群上传/下载逻辑（这部分是 region 后端职责）；SHALL NOT 落地 backup 的备份数据加密（rainbond 历史用 AuthCode + KEY，待后续独立 hardening）
- **依赖**：无硬依赖；与 P0 段 8 个子 change + P1 段 5 个子 change 全部解耦

## 路线位置

- 母路线图：[`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/)
- 优先级：**P2 #5**
- 估计 method 数：5（与路线图一致，含 4 既有 method URL 修正不增加新 method 数）
- rainbond 参照：`console/services/backup_service.py` + `www/apiclient/regionapi.py:1685-1746`
- 与其它 P2 子 change 的关系：完全独立，可与 P2 #1 / #2 / #3 / #4 并行实施
