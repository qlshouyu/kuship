## ADDED Requirements

### Requirement: 应用模板（rainbond_center_app）CRUD 端点

kuship-console SHALL 暴露与 rainbond-console 100% 兼容的应用模板 CRUD 端点，覆盖 `/enterprise/{eid}/app-models{,/<app_id>{,/version/<ver>}}` 共 5 path 的 GET/POST/PUT/DELETE。模板数据 SHALL 通过 `RainbondCenterApp`（rainbond_center_app）+ `RainbondCenterAppVersion`（rainbond_center_app_version）两个 Entity 落地，列名严格与 schema 真相对齐（保留 `is_ingerit` 历史拼写不修复）。

#### Scenario: GET /enterprise/{eid}/app-models 列表 + tag 过滤 + 分页

- **WHEN** 调 `GET /console/enterprise/E1/app-models?page=1&page_size=10&tag_id=2&scope=enterprise`
- **THEN** kuship-console 用 JPQL 三表 join `rainbond_center_app` + `rainbond_center_app_tag_relation`+`rainbond_center_app_tag` 查询
- **AND** 响应 `data.list` 数组，`data.bean.total` 总数
- **AND** 每条 item 含 app_id / app_name / pic / scope / dev_status / is_official / install_number 等字段

#### Scenario: POST /enterprise/{eid}/app-models 创建模板

- **WHEN** 调 POST body `{"app_name":"my-app","scope":"enterprise","describe":"..."}`
- **THEN** kuship-console 生成 32-char `app_id` UUID
- **AND** 写入 `rainbond_center_app` 一行（is_official=0、install_number=0、is_ingerit=0、enterprise_id=E1）
- **AND** 响应 `data.bean.app_id`

#### Scenario: GET /app-model/{app_id}/version/{version} 模板版本详情

- **WHEN** 调 GET 含 `app_template` 大字段
- **THEN** kuship-console 读 `rainbond_center_app_version` 单行，原样返回

### Requirement: 应用模板 Tag CRUD 端点

kuship-console SHALL 实现 `/enterprise/{eid}/app-models/tag` Tag 字典 CRUD（GET 列表 / POST 新建 / PUT/DELETE `tag/{tag_id}`）+ `/enterprise/{eid}/app-model/{app_id}/tag` 关联绑定/解绑（POST 绑定、DELETE 解绑），共 5 endpoint；落地表为 `rainbond_center_app_tag` + `rainbond_center_app_tag_relation`。

#### Scenario: POST /tag 创建 Tag

- **WHEN** 调 POST body `{"name":"web"}`
- **THEN** kuship-console 写入 `rainbond_center_app_tag` (name='web', enterprise_id=E1, is_deleted=0)
- **AND** 响应 tag_id 整数

#### Scenario: POST /app-model/{app_id}/tag 绑定 Tag

- **WHEN** 调 POST body `{"tag_id": 5}`
- **THEN** kuship-console 写入 `rainbond_center_app_tag_relation` (app_id=, tag_id=5, enterprise_id=E1)

### Requirement: 远程应用市场（AppMarket）凭据端点

kuship-console SHALL 实现 `/enterprise/{eid}/cloud/markets`（GET/POST 列表 + 创建）/ `cloud/bind-markets`（POST 批量绑定）/ `cloud/markets/{name}` (GET/PUT/DELETE) / `cloud/bindable-markets` (GET 可绑列表) / `cloud/markets/{name}/app-models{,/{model_id}/{versions,version/{v}}}`（GET 浏览远程模板及其版本）共 8 endpoint。`AppMarket` 凭据 Entity 落地 `app_market` 表。

#### Scenario: GET /cloud/markets 列出已绑定市场

- **WHEN** 调 GET
- **THEN** 返回 list 含 name / domain / type / access_key_masked（access_key 用 `***xx` 掩码）

#### Scenario: GET /cloud/markets/{name}/app-models 透传远程市场模板

- **WHEN** 调 GET
- **THEN** kuship-console 用本地 access_key + url 远程调用对应市场 API
- **AND** 响应原样透传 `data.list`

### Requirement: 从模板创建组件端点

kuship-console SHALL 实现 `POST /teams/{team_name}/apps/market_create` —— 接收 `{app_id, version, group_id, region_name}` 拉取模板 → 创建 group 内全部 service + ports + envs + relations；`POST /teams/{team_name}/apps/cmd_create` 同时支持 `helm` / `image` / `source_code` 三种 kind 命令行式安装。复用 `AppCreateService.create()`（appcreate 模块）逐个建组件。

#### Scenario: POST /apps/market_create 从模板创建多组件

- **WHEN** 调 body `{"app_id":"abc","version":"1.0","group_id":42}`
- **THEN** kuship-console 读 `rainbond_center_app_version.app_template` 解出 components 数组
- **AND** 对每个 component 调 `AppCreateService.create()` 创建 tenant_service + service_source
- **AND** 写 `service_group_relation` 关联到 group_id=42
- **AND** 响应 `data.bean.created` 数量

#### Scenario: POST /apps/cmd_create 命令行安装 helm

- **WHEN** 调 body `{"kind":"helm","command":"helm install foo bar/baz","group_id":42}`
- **THEN** kuship-console 调 `HelmOperations.commandInstall(region, team, command)`
- **AND** 写 `team_helm_release_source` 记录

### Requirement: 单组件版本快照与回滚端点

kuship-console SHALL 实现 `/teams/{team_name}/apps/{service_alias}/version` GET 列表 + `/version/{version_id}` GET 详情 + POST 回滚 + `/version/snapshot` 列表 + `/version/snapshot/{snap_id}` 详情 + `/version/rollback` POST + `/version/rollback/records` GET + `/rollback/records/{record_id}` GET 共 8 endpoint。版本数据由 region 持有，console 仅做查询透传与回滚触发。

#### Scenario: GET /apps/{alias}/version 版本列表

- **WHEN** 调 GET
- **THEN** kuship-console 调 region `/v2/tenants/{}/services/{}/versions` 透传

#### Scenario: POST /apps/{alias}/version/{version_id} 回滚到指定版本

- **WHEN** 调 POST body `{"deploy_version":"v1.0"}`
- **THEN** kuship-console 调 region `/rollback` 触发回滚 + 拿 event_id
- **AND** 本地不写新表，仅 update_time + update_version+1

### Requirement: 应用整组升级端点

kuship-console SHALL 实现 `/teams/{team_name}/groups/{group_id}/upgrade-records` GET 列表 + POST 创建 + `/upgrade-records/{record_id}` GET 详情 + `/upgrade-records/{record_id}/{upgrade,deploy,rollback,info,detail,components}` 共 9 endpoint。`AppUpgradeRecord` Entity 落地 `app_upgrade_record` 表，子表 `app_upgrade_snapshots` + `service_upgrade_record` 通过 record_id 关联。

#### Scenario: POST /upgrade-records 创建升级记录

- **WHEN** 调 POST body `{"version":"v2.0","upgrade_group_id":42,"market_name":"local"}`
- **THEN** kuship-console 写 `app_upgrade_record` 一行 status=0 record_type=upgrade
- **AND** 响应 record_id

#### Scenario: POST /upgrade-records/{record_id}/upgrade 推送至 region

- **WHEN** 调 POST
- **THEN** kuship-console 调 region 升级 API → 拿 event_id → update record.status=1 + event_id

#### Scenario: POST /upgrade-records/{record_id}/rollback 触发回滚

- **WHEN** 调 POST
- **THEN** kuship-console 在事务内 INSERT 新 record_type=rollback 行（parent_id=原 record_id）→ 调 region rollback

### Requirement: 服务分享异步流程端点

kuship-console SHALL 实现 `/teams/{team_name}/groups/{group_id}/share/record` POST 启动 + DELETE 取消 + GET version + `/share/{share_id}/{info,events{,/{event_id}{,/plugin}},giveup,complete}` 共 11 endpoint。`ServiceShareRecord` Entity 落地 `service_share_record` 表，事件流落 `service_share_record_event` 表；保留 rainbond 原 6 阶段 step 与 3 阶段 status 状态机不重新设计。

#### Scenario: POST /share/record 启动分享

- **WHEN** 调 POST body `{"share_app_market_name":"local","share_version":"1.0","share_app_model_name":"my-app"}`
- **THEN** kuship-console 写 `service_share_record` 一行（step=0、status=0、share_version、share_app_model_name）
- **AND** 响应 share_id

#### Scenario: POST /share/{share_id}/info 推送应用模板

- **WHEN** 调 POST body 含 app_template 等字段
- **THEN** kuship-console 调 region 服务分享 API → 拿到 event_id → 写 `service_share_record_event` 一行 + update record.step=2

#### Scenario: POST /share/{share_id}/complete 完成分享

- **WHEN** 调 POST
- **THEN** kuship-console update record status=1 step=5

### Requirement: 应用模板导出与导入端点

kuship-console SHALL 实现 `/enterprise/{eid}/app-models/export` POST 导出（生成 zip 流式返回）、`/app-models/import` POST 启动导入 + `/import/{event_id}` GET 状态轮询 + `/import/{event_id}/dir` GET 目录预览 共 4 endpoint。导出 SHALL 在 console 端 java.util.zip 内存压缩；导入由 region 处理（console 仅持久化 event_id + status）。

#### Scenario: POST /app-models/export 导出为 zip

- **WHEN** 调 POST body `{"app_ids":["a1","a2"],"format":"rainbond-app"}`
- **THEN** kuship-console 用 `ZipOutputStream` 流式生成 zip 含 metadata.json + 各 app_template
- **AND** 响应 Content-Disposition: attachment; filename=...

#### Scenario: POST /app-models/import 启动导入

- **WHEN** 调 POST 含 file
- **THEN** kuship-console 调 region 导入 API → 拿 event_id → 写 `groupapp_backup_import` 一行
- **AND** 响应 event_id

### Requirement: Helm Chart 应用安装端点

kuship-console SHALL 实现 `/teams/{team_name}/{helm_app, helm_command, helm_list, helm_cmd_add, helm_center_app}` 5 endpoint + 全局 `/helm/repos` GET/POST/DELETE 共 7 endpoint。`HelmRepo` Entity 落地 `helm_repo` 表；密码 SHALL 用 AES-GCM 加密后存（密钥来自 `kuship.helm.repo-password-key` 配置）；prod profile 缺密钥时启动失败。

#### Scenario: POST /helm/repos 添加 Repo

- **WHEN** 调 POST body `{"name":"bitnami","url":"https://charts.bitnami.com/bitnami","username":"","password":""}`
- **THEN** kuship-console 写 `helm_repo` 一行（password 加密）
- **AND** 调 region `HelmOperations.addRepo(...)` 通知 region

#### Scenario: POST /teams/{team}/helm_app 从 Chart 安装

- **WHEN** 调 POST body `{"chart_name":"redis","repo":"bitnami","version":"19.0.0","values":"..."}`
- **THEN** kuship-console 调 `HelmOperations.installChart(...)` → 拿 release_id
- **AND** 写 `team_helm_release_source` 一行 + `app_helm_overrides` 一行（values）

#### Scenario: POST /teams/{team}/helm_command 命令行安装

- **WHEN** 调 POST body `{"command":"helm install foo bar/baz --set k=v"}`
- **THEN** 调 `HelmOperations.commandInstall(...)` 直接透传命令字符串

### Requirement: 整组备份与导入端点

kuship-console SHALL 实现 `/teams/{team_name}/groupapp/{group_id}/{backup,backup/all_status,backup/export,backup/import}` + `/groupapp/backup`（团队级列表）+ `/all/groupapp/backup`（全企业列表）+ `/enterprise/{eid}/{backups,backups/{name},upload-backups}` 共 9 endpoint。`ServiceGroupBackup` Entity 落地 `groupapp_backup` 表；`BackupOperations` 接口承载 region 调用。

#### Scenario: POST /groupapp/{group_id}/backup 启动整组备份

- **WHEN** 调 POST body `{"note":"weekly","mode":"full"}`
- **THEN** kuship-console 写 `groupapp_backup` 一行 status=starting + backup_id 32-char UUID
- **AND** 调 `BackupOperations.backup(region, team, body)` 触发 region 异步备份
- **AND** 响应 backup_id

#### Scenario: GET /groupapp/{group_id}/backup/all_status 轮询状态

- **WHEN** 前端按 5s 轮询调 GET
- **THEN** kuship-console 读本地 `groupapp_backup.status` 直接返回（不每次重打 region）

#### Scenario: POST /groupapp/{group_id}/backup/import 导入备份

- **WHEN** 调 POST 含 file
- **THEN** kuship-console 写 `groupapp_backup_import` + 调 region 恢复

### Requirement: 整组应用复制与迁移端点

kuship-console SHALL 实现 `/teams/{team_name}/groupapp/{group_id}/copy` POST（同 region 复制）+ `/groupapp/{group_id}/migrate` POST（跨 region 迁移）+ `/groupapp/{group_id}/migrate/record` GET 共 3 endpoint。

#### Scenario: POST /groupapp/{group_id}/copy 同 region 复制

- **WHEN** 调 POST body `{"target_team_name":"team2","new_group_name":"my-app-copy"}`
- **THEN** kuship-console 列出 group 全部 service → 复用 `AppCreateService.create()` 逐个新建至 target_team
- **AND** 响应 `data.bean.new_group_id`

#### Scenario: POST /groupapp/{group_id}/migrate 跨 region 迁移

- **WHEN** 调 POST body `{"target_region":"r2","target_team":"team2"}`
- **THEN** 调 region migrate API → 写 `service_group_migration` 一行 + 返回 record_id

### Requirement: 团队 image_tags 列表端点

kuship-console SHALL 实现 `GET /teams/{team_name}/apps/image_tags?image=xxx` —— 调 hub registry HTTP API 拿 image 的 tag 列表。复用第 5 阶段 `team_registry_auths` 凭据；timeout 5s；失败返回空数组。

#### Scenario: GET /apps/image_tags 拉取公网 hub tags

- **WHEN** 调 GET `?image=nginx`
- **THEN** kuship-console 公网调 `https://registry-1.docker.io/v2/library/nginx/tags/list`
- **AND** 5 秒内返回 list；超时返回 `data.list=[]`

### Requirement: appmarket 模块 10 张表的 JPA Entity 与 Repository

kuship-console SHALL 在 `cn.kuship.console.modules.appmarket.{market,share,upgrade,backup,helm}.entity` 包下新增以下 Entity：
1. `RainbondCenterApp`（rainbond_center_app，19 列含 is_ingerit）
2. `RainbondCenterAppVersion`（rainbond_center_app_version，25 列）
3. `CenterAppTag`（rainbond_center_app_tag，4 列：id/name/enterprise_id/is_deleted）
4. `CenterAppTagRelation`（rainbond_center_app_tag_relation，4 列）
5. `AppMarket`（app_market）
6. `ServiceShareRecord`（service_share_record，19 列）
7. `ServiceShareRecordEvent`（service_share_record_event）
8. `AppUpgradeRecord`（app_upgrade_record，17 列）
9. `ServiceGroupBackup`（groupapp_backup）
10. `HelmRepo`（helm_repo）

主键全部 Integer 自增；列名与 schema 真相严格对齐，不擅自加 create_time / update_time（除非 DESC 已显示存在）。

#### Scenario: ddl-auto=validate 启动通过

- **WHEN** 应用启动连真实 MySQL（rainbond docker compose）
- **THEN** Hibernate ddl-auto=validate 不报缺列 / 多列 / 错类型错误

### Requirement: HelmOperations 6 method 完整实现

kuship-console SHALL 在 `appmarket/helm/api/HelmOperationsImpl.java` 中标注 `@Service @Primary` 替换 14 接口骨架的 unsupported 占位，实现 6 method：addRepo / removeRepo / listChart / queryChart / installChart / commandInstall。

#### Scenario: HelmOperations 完整 6 method 接通

- **WHEN** controller 注入 `HelmOperations` 调用任一 method
- **THEN** Impl 用 `RegionApiSupport.exchange(lambda)` 模板调 region `/v2/helm/*` 路径
- **AND** 不再抛 UnsupportedOperationException

### Requirement: BackupOperations 新接口 4 method 实现

kuship-console SHALL 在 `appmarket/backup/api/` 包下新建 `BackupOperations` 接口（4 method：backup / backupStatus / restore / export）+ Impl，作为本 change 的非骨架新增 region 接口。

#### Scenario: BackupOperations.backup 触发 region 备份

- **WHEN** controller 调用 `backupOperations.backup(region, team, body)`
- **THEN** Impl 调 region `/v2/tenants/{tenant}/groupapp/{group_id}/backup` 拿 backup_id
- **AND** 同步响应给 controller 写本地 groupapp_backup 元数据

### Requirement: appmarket 模块测试覆盖

kuship-console SHALL 提供至少 5 类集成测试覆盖 appmarket 核心：
1. `MarketTemplateIntegrationTest`：rainbond_center_app POST/GET CRUD + tag 绑定
2. `ShareRecordIntegrationTest`：service_share_record 启动 → info → events → complete 全状态机
3. `AppUpgradeIntegrationTest`：upgrade-records POST/GET + upgrade 推送 + rollback
4. `GroupBackupIntegrationTest`：groupapp/backup POST 写本地 + region mock 调用
5. `HelmRepoIntegrationTest`：helm/repos POST 双写本地 + region mock + 密码加密验证

#### Scenario: 集成测试全部使用真实 MySQL

- **WHEN** 在 docker-compose 启动后跑 `mvn -Dtest='cn.kuship.console.modules.appmarket.**' test`
- **THEN** 每类测试在 `@BeforeAll` 用高位 user_id（9091xx）插入 user/team 数据
- **AND** 在 `@AfterAll` 清理避免数据残留
- **AND** 全部用例通过

#### Scenario: HelmRepoIntegrationTest 验证密码加密

- **WHEN** POST `/helm/repos` 含 password=secretkey
- **THEN** 测试断言 `helm_repo.password` 列存的不是 'secretkey' 明文（说明 AES 加密生效）
- **AND** 调 GET 时返回的 password 字段为 `***`（掩码）
