## Why

`migrate-region-coverage-roadmap` 把"组件构建版本 / 部署版本 / 源码检测 / 多语言版本 / 批量操作"列为
**P1 #4**，是路线图中 **method 数最大（15）** 的子 change，同时也是 UI 应用页"版本"Tab、构建中心、
多语言版本管理页面、组件批量启停按钮的后端真相源。当前 kuship-console 的 `ServiceOperations` 接口仍持有
4 个 default unsupported 桩（无 buildVersions / deployVersion / checkInfo / sourceCheck / buildStatus 中的任何一个），
`AppBatchActionsController` 走"按 service_id 循环挨个调 lifecycle"的退化实现，多语言版本（lang version）和
CNB framework 完全没有 region 调用入口。

清掉这 15 个 method 的桩 + 补齐两张本地表 + 三个 controller，等价于一次性把 rainbond `regionapi.py`
中 `get_service_build_versions / get_service_build_version_by_id / update_service_build_version_by_id /
delete_service_build_version / get_service_deploy_version / get_team_services_deploy_version /
get_service_check_info / service_source_check / get_build_status / get_lang_version / create_lang_version /
update_lang_version / delete_lang_version / get_cnb_frameworks / batch_operation_service` 全套迁移到位。

迁完之后立刻解锁三件事：

1. UI 组件页"版本"Tab 能直出 region 真实构建版本列表 + 当前 deploy_version + 升级/回滚按钮态判断
2. UI 应用页"批量操作"按钮一次性下发到 region `/v2/tenants/{tn}/batchoperation`，不再 N 次单调度（rainbond 5.1+ 行为）
3. 多语言版本管理页（slug / cnb 双策略）和源码检测异步三段式（check uuid → poll → result）后端齐全

依赖关系：

- 无强依赖。`get_lang_version` 与 P2 子 change `migrate-console-maven-setting` 在多语言版本读取上有少量重叠
- 决策：lang version CRUD 完整由本 change 拥有；`migrate-console-maven-setting` 只引用本 change 落地的 `LangVersionOperations`，不重写
- `batch_operation_service` 与 `migrate-console-app-runtime` 已落地的 `AppBatchActionsController` 路径一致
  （`POST /console/teams/{team_name}/batch_actions`），本 change 把现有 controller 内的 N 次 lifecycle 循环改造成
  1 次 `batchOperationService` 调用，保持 URL 与权限码不变；同时保留 fallback 路径（region 不支持时退化）

## What Changes

### 实现 Region Operations 接口扩 + 新增

**扩 `ServiceOperations`**（在已落地的 7 method 之上 +5 个新 method，全部 default unsupported），
落地 `cn.kuship.console.modules.application.api.ServiceOperationsImpl`（`@Primary`）的 5 个 override：

- `getBuildVersions(regionName, tenantName, serviceAlias)` → GET `/v2/tenants/{tenant_name}/services/{service_alias}/build-list` —— rainbond 锚点 `regionapi.py:1752 get_service_build_versions`
- `getBuildVersionById(regionName, tenantName, serviceAlias, versionId)` → GET `/v2/tenants/{tenant_name}/services/{service_alias}/build-version/{version_id}` —— `regionapi.py:1775`
- `updateBuildVersion(regionName, tenantName, serviceAlias, versionId, body)` → PUT 同路径 —— `regionapi.py:1787`
- `deleteBuildVersion(regionName, tenantName, serviceAlias, versionId, body)` → DELETE 同路径 —— `regionapi.py:1763`
- `getServiceDeployVersion(regionName, tenantName, serviceAlias)` → GET `/v2/tenants/{tenant_name}/services/{service_alias}/deployversions` —— `regionapi.py:1809`
- `getTeamServicesDeployVersion(regionName, tenantName, body)` → POST `/v2/tenants/{tenant_name}/deployversions` —— `regionapi.py:1799`
- `getServiceCheckInfo(regionName, tenantName, uuid)` → GET `/v2/tenants/{tenant_name}/servicecheck/{uuid}` —— `regionapi.py:1450`
- `serviceSourceCheck(regionName, tenantName, body)` → POST `/v2/tenants/{tenant_name}/servicecheck` —— `regionapi.py:1440`
- `getBuildStatus(regionName, tenantName, pluginId, buildVersion)` → GET `/v2/tenants/{tenant_name}/plugin/{plugin_id}/build-version/{build_version}` —— `regionapi.py:1264`

> 实际签名上述 9 个 method 都加在 `ServiceOperations`，但路线图统计为"+5 关于版本"是把 buildVersion 4 method
> + deployVersion 2 method + sourceCheck 2 method + buildStatus 1 method 折算成"5 类"。本 change 严格按 9 个
> method 落地（与 rainbond Python 一一对应），统计口径不影响实施。

**新增 `LangVersionOperations`**（`modules/application/api/`），5 method：

- `getLangVersion(enterpriseId, regionName, lang, show, buildStrategy)` → GET `/v2/cluster/langVersion?language=&show=&build_strategy=` —— `regionapi.py:2791`
- `createLangVersion(enterpriseId, regionName, body)` → POST `/v2/cluster/langVersion` —— `regionapi.py:2813`
- `updateLangVersion(enterpriseId, regionName, body)` → PUT `/v2/cluster/langVersion` —— `regionapi.py:2822`
- `deleteLangVersion(enterpriseId, regionName, body)` → DELETE `/v2/cluster/langVersion`（DELETE with body） —— `regionapi.py:2831`
- `getCnbFrameworks(enterpriseId, regionName, lang)` → GET `/v2/cluster/cnb/frameworks?lang=` —— `regionapi.py:2840`

**新增 `BatchServiceOperations`**（`modules/application/api/`），1 method：

- `batchOperationService(regionName, tenantName, body)` → POST `/v2/tenants/{tenant_name}/batchoperation` + Header `Resource-Validation: true` —— `regionapi.py:1893`

### 新增 console controller（3 个，11 endpoint）

按 rainbond `console/urls/__init__.py` 行号锚点（路径变量统一 snake_case，trailing slash 双声明）：

- `AppVersionsController`（`/console/teams/{team_name}/apps/{service_alias}/{build-versions, build-versions/{version_id}, deploy-version, source-check, source-check/{uuid}, build-status}`）—— 6 endpoint，覆盖构建版本列表 / 详情 / 更新 / 删除 / 部署版本单查 / 源码检测发起 / 源码检测结果 / 构建状态查询
- `LangVersionController`（`/console/enterprise/{enterprise_id}/regions/{region_name}/{lang-version, cnb/frameworks}`）—— 5 endpoint（GET / POST / PUT / DELETE lang-version + GET cnb/frameworks）
- `BatchDeployVersionController`（`/console/teams/{team_name}/deploy-version`）—— 1 endpoint（POST 批量查 deploy_version，包含 `service_ids` body）

**改造既有** `AppBatchActionsController`（位于 `modules/appruntime/controller/`）：把 N 次 lifecycle 循环
替换为 1 次 `batchOperationService` 调用，保持 URL `/console/teams/{team_name}/batch_actions` + 权限码 `APP_OVERVIEW_PERMS` 不变；
保留兼容降级（region 不支持 batchoperation 端点 → 透传 region 错误而非内部循环）。

### 新增本地 entity（2 张）

- `ServiceBuildVersion`（`service_build_version` 表，按 region Go 端 `db/model/service_build_version.go` schema 真相）—— 用于本地缓存最近构建版本列表（**只读缓存策略**，写入路径为 region 推送或定时同步；本 change 仅落表 + repository，不引入写路径，避免与 region 真相源冲突）
- `LangVersion`（`lang_version` 表，按 region Go 端 `db/model/lang_version.go` schema 真相）—— 同样只读缓存

> 决策：两张 entity 在本 change **只落 JPA 映射 + 验证 schema**（hibernate `validate` 模式），不暴露给业务写入路径；
> 业务读取一律走 region 透传。entity 落地是为后续 `add-build-version-cache` / `add-component-list-deploy-version-cache`
> 等 hardening change（决策 5 提到的"高频 deploy_version 客户端 cache"）能直接复用 entity 而非在那时再补 schema。

### 不在本 change 内（明确推迟）

- **`get_team_services_deploy_version` 客户端缓存**（UI 列表页一次拉 N 个 service 的 deploy_version）→ `add-component-list-deploy-version-cache` hardening；本 change 纯透传
- **构建版本本地写入**（service_build_version 表的写路径）→ 同上 hardening
- **多语言版本本地写入**（lang_version 表的写路径）→ 同 hardening
- **maven-setting 相关 8 method**（`get_maven_setting / list_maven_settings / add_maven_setting / update_maven_setting / delete_maven_setting / get_protocols` 等）→ P2 子 change `migrate-console-maven-setting`
- **源码检测的复杂 build_strategy 推断**（cnb / slug / dockerfile 三模式自动选择）→ rainbond `console/services/source_build_state_service.py` 的业务规则保留在 rainbond-console 行为不变；kuship 端只透传 region 调用，不在 console 层做 strategy 推断

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "构建版本与多语言版本管理"。覆盖 11 endpoint 的契约、`ServiceOperations` +9 method / `LangVersionOperations` 5 method / `BatchServiceOperations` 1 method 的 region URL 路径与响应透传约束、两张本地 entity 的 schema 真相、与 `migrate-console-maven-setting` 子 change 的解耦边界（lang version CRUD 由本 change 拥有）、与既有 `AppBatchActionsController` 改造的兼容承诺。

## Impact

- **代码新增**：
  - controller：3 个新增（`modules/application/controller/version/`、`modules/region/controller/lang/`）+ 1 个改造（`AppBatchActionsController`）
  - region API：扩 `ServiceOperations` +9 method（接口 default + Impl @Primary override）；新接口 `LangVersionOperations` 5 method + `BatchServiceOperations` 1 method（`Default + Impl @Primary` 双 bean 模式，与既有 14 接口骨架保持一致风格）
  - entity：2 张新增（`ServiceBuildVersion` ≈ 18 列 / `LangVersion` ≈ 8 列）+ 对应 repository（仅 finder，无 writer）
  - 单测：`ServiceOperationsImplVersionTest`（9 method × happy + 1 region 5xx 透传）+ `LangVersionOperationsImplTest`（5 method × happy + 1 透传）+ `BatchServiceOperationsImplTest`（1 method + Resource-Validation 头断言）+ 3 个 controller 集成测试 + 1 个 `AppBatchActionsController` 改造回归测试
- **数据库**：无变更（hibernate `validate` 模式校验既存表，schema 由 region Go 端 / rainbond Django 维护）
- **依赖**：不引入新 maven 依赖
- **跨 change 衔接**：
  - `migrate-console-maven-setting`（P2）后续将引入 `MavenSettingOperations` 新接口，**复用**本 change 落地的 `LangVersionOperations` 而非重写；多语言版本 CRUD 不在 maven-setting 中重复
  - `add-component-list-deploy-version-cache`（hardening）将基于本 change 的 `getTeamServicesDeployVersion` 透传增加 N+1 缓存层，不动接口签名
  - `add-build-version-cache`（hardening）将给 `service_build_version` 本地表加写路径（rainbond 推 build event 后落库），不动 entity schema
- **不影响**：rainbond-console（仍可独立跑 7070）、其他已迁移 change
- **路径变量**：路径中 `{team_name}` / `{service_alias}` / `{version_id}` / `{uuid}` / `{enterprise_id}` / `{region_name}` 全部 snake_case
- **14 接口骨架进度**：`ServiceOperations` 由现有 7/15 推进到 16/16（含本 change 新增 9 method）—— 接口完整；新增 `LangVersionOperations` / `BatchServiceOperations` 不算 14 接口骨架（属业务域非通用骨架，与 `BackupOperations` / `MonitorOperations` 同等地位）
