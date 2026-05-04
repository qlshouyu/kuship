## Why

第 8 阶段（migrate-console-app-runtime）打通了组件 "跑起来" 全套；但 **应用复用 / 模板生态 / 备份迁移 / Helm Chart 安装** 这 4 大批量化能力还停留在 rainbond-console。kuship-ui 的 "应用市场" 标签页、"备份/迁移" 抽屉、"Helm 商店" 入口全部空白。本次把 rainbond `app_market.py` / `service_share.py` / `helm_app.py` / `app_version.py` / `app_upgrade.py` / `backup_data.py` / `center_pool/{apps.py, app_export.py, app_import.py, groupapp_backup.py, groupapp_copy.py, groupapp_migration.py}` 共 12 个 view 文件 ~3000 行迁移到 kuship-console，覆盖 ~50 endpoint。

## What Changes

- **应用模板（app-models）CRUD**：`/enterprise/{eid}/app-models` 列表/创建、`/app-model/{app_id}` 详情/更新/删除、`/app-model/{app_id}/version/{version}` 版本 CRUD —— 走 `rainbond_center_app` + `rainbond_center_app_version` 两表。
- **应用模板 Tag**：`/enterprise/{eid}/app-models/tag` Tag CRUD + `/app-model/{app_id}/tag` 关联绑定/解绑 —— `app_tag` + `tag_info` 两表。
- **应用市场（云端模板源）**：`/enterprise/{eid}/cloud/markets` CRUD、`/cloud/bind-markets` 批量绑定、`/cloud/bindable-markets` 可绑列表、`/cloud/markets/{name}/app-models{,/{model_id}/{versions,version/{v}}}` 远程 app-model 浏览 + 安装。
- **从模板创建组件**：`/teams/{team_name}/apps/market_create`（POST 走完整 group + components + relations 创建）、`/teams/{team_name}/apps/cmd_create`（命令行式 helm cmd / image / source 多源安装）。
- **应用版本快照与回滚**：`/teams/{team_name}/apps/{alias}/version` GET 列表、`/{version_id}` GET/POST 回滚 + `groups/{group_id}/version{,/snapshot{,/{snap_id}}}` group 级整组快照 + `version/rollback{,/records,/records/{record_id}}` 回滚记录。
- **应用升级**：`/teams/{team_name}/groups/{group_id}/upgrade-records{,/{record_id}{,/upgrade,/deploy,/rollback,/info,/detail,/components}}` —— `app_upgrade_record` 表 6 endpoint 控制升级生命周期。
- **服务分享（导出为模板）**：`/teams/{team_name}/groups/{group_id}/share/record` POST 启动分享 / DELETE 取消、`/share/{share_id}/{info,events,events/{event_id},events/{event_id}/plugin,giveup,complete}` —— `service_share_record` + `app_export_record` 两表的全异步流程。
- **应用导入/导出**：`/enterprise/{eid}/app-models/{import,export}` POST + `/import/{event_id}{,/dir}` GET 状态轮询。
- **Helm Chart 应用**：`/teams/{team_name}/{helm_app, helm_command, helm_list, helm_cmd_add, helm_center_app}` 5 endpoint + `/helm/repos` 全局 repo CRUD —— `helm_repo` 表（kuship 复用 rainbond 表）。
- **组件 image_tags 查询**：`/teams/{team_name}/apps/image_tags` GET（基于 hub registry auth 列出镜像 tag 列表）。
- **整组备份 / 复制 / 迁移**：`/teams/{team_name}/groupapp/{group_id}/{backup{,/all_status,/export,/import},copy,migrate{,/record}}` + `/teams/{team_name}/groupapp/backup`、`/teams/{team_name}/all/groupapp/backup`、`/enterprise/{eid}/backups{,/{backup_name},/upload-backups}` —— `service_group_backup` + `service_group_migration` 两表。
- **Region API 扩展**：`HelmOperations` 6 method（addRepo / listChart / installChart / commandInstall / queryChart / removeRepo），新增 `BackupOperations`（4 method：backup / backupStatus / restore / export）—— 与 14 接口骨架的 `HelmOperations` 等占位 method 替换；BackupOperations 是非骨架新接口。

## Capabilities

### Modified Capabilities

- `kuship-console-app`: 新增约 18 条市场 / 模板 / 分享 / 备份 / 升级 / Helm 相关 Requirement，并补齐对应的 7 张本地表（`rainbond_center_app`、`rainbond_center_app_version`、`tag_info`、`app_tag`、`app_market`、`service_share_record`、`app_export_record`、`app_upgrade_record`、`service_group_backup`、`helm_repo` —— 共 10 张，部分已存在于 schema）的 Entity / Repository。

## Impact

- **新增包**：`cn.kuship.console.modules.appmarket/`（controller + service + entity + repository + dto），与 `appruntime/` 平级；其内容很大，按子域再细分：`market/`、`share/`、`upgrade/`、`backup/`、`helm/`、`version/`。
- **新增 Entity**：`RainbondCenterApp`、`RainbondCenterAppVersion`、`TagInfo`、`AppTag`、`AppMarket`（远程 marketplace 凭据）、`ServiceShareRecord`、`AppExportRecord`、`AppUpgradeRecord`、`ServiceGroupBackup`、`HelmRepo` —— 共 **10 张表 JPA Entity**（实际列名以 schema 真相为准；前一阶段 autoscaler 教训：必须先查 `DESC <table>` 再写 entity）。
- **新增 Region API 实现**：`HelmOperations` 完整 6 method 实现（替换骨架占位）；新增 `BackupOperations` 接口 + 实现 4 method。
- **依赖**：保持现有 `RestClient` + `JPA`，不引入 helm-client SDK（commandInstall 是 region 端拉 chart，console 仅传 chart_url + values.yaml）。
- **测试**：扩展 ~5 个集成测试覆盖 market CRUD / share record / backup / upgrade / helm。
