## Why

`migrate-console-app-market` 已落地了 `service_share_record` / `service_share_record_event` / `tenant_plugin_share` / `plugin_share_record_event` 四张表的 entity / repository，并在 `ServiceShareController`（13 endpoint）与 `PluginShareController`（5 endpoint）上搭好了 6-step / 3-status 状态机骨架；UI 端"应用分享 / 插件分享 / 发布历史"页面也能读到本地记录。但状态机内部所有应当推进到 region 的关键步骤（同步事件 / 触发分享 / 拉取结果 / 查询发布状态）目前**全部为本地占位**：`pluginEvent` 直接 `return GeneralMessage.ok()`、`addEvent` 仅落库不调 region、`complete` 仅本地翻 `status=1` —— 用户点完"开始分享"按钮后，region Go 端的 `service_share` / `plugin_share` 任务从未被触发，分享按钮一直卡在 step=2 / step=3。

`migrate-region-coverage-roadmap` 把这块归为 **P1 #2**（应用分享 / 6-step 状态机），共 7 个 region method、URL 前缀 `/v2/cloud-service/*` 与 `/v2/tenants/{name}/share/*`，依赖项为零（state machine + entity 骨架已就绪）。本 change 完整迁移 rainbond `regionapi.py` 中的：

- `share_clound_service`（v3.5 前云市直推，保留兼容）
- `share_service` / `share_service_result`（应用分享触发与轮询）
- `share_plugin` / `share_plugin_result`（插件分享触发与轮询）
- `get_service_publish_status`（按 service_key + version 查发布态）
- `list_app_releases`（按 region app_id 列发布历史）

并在 `ServiceShareController` 与 `PluginShareController` 既有的 6-step 状态机内部把这些 region 调用接线进去 —— **保留 controller 路径不变**、保留 ServiceShareRecord / TenantPluginShare 等本地 entity 形状不变，仅替换内部"伪推进 step"为"真实推进 step + region 调用"。

## What Changes

### 新增 Operations 接口（7 method，非 14 核心骨架）

新建 `cn.kuship.console.modules.appmarket.share.api.ShareOperations` 接口（包路径与 controller 同模块，遵守路线图决策 4 "非 14 核心放业务模块"），承载 7 个 region API method：

- `shareCloudService(regionName, tenantName, body)` → POST `/v2/tenants/{namespace}/cloud-share` —— 锚点 `regionapi.py:975 share_clound_service`
- `shareService(regionName, tenantName, serviceAlias, body)` → POST `/v2/tenants/{namespace}/services/{service_alias}/share` —— 锚点 `regionapi.py:987 share_service`
- `getShareServiceResult(regionName, tenantName, serviceAlias, regionShareId)` → GET `/v2/tenants/{namespace}/services/{service_alias}/share/{region_share_id}` —— 锚点 `regionapi.py:996 share_service_result`
- `sharePlugin(regionName, tenantName, pluginId, body)` → POST `/v2/tenants/{namespace}/plugins/{plugin_id}/share` —— 锚点 `regionapi.py:1006 share_plugin`
- `getSharePluginResult(regionName, tenantName, pluginId, regionShareId)` → GET `/v2/tenants/{namespace}/plugins/{plugin_id}/share/{region_share_id}` —— 锚点 `regionapi.py:1015 share_plugin_result`
- `getServicePublishStatus(regionName, tenantName, serviceKey, appVersion)` → GET `/v2/builder/publish/service/{service_key}/version/{app_version}` —— 锚点 `regionapi.py:1331 get_service_publish_status`
- `listAppReleases(regionName, tenantName, regionAppId)` → GET `/v2/tenants/{namespace}/apps/{app_id}/releases` —— 锚点 `regionapi.py:2389 list_app_releases`

### 新增 ShareOperationsImpl（@Primary @Service）

`cn.kuship.console.modules.appmarket.share.api.ShareOperationsImpl` 同包落地 7 个 method 的实现：

- 通过 `RegionClientFactory.client(enterpriseId, regionName)` 取 mTLS RestClient
- `tenant_name` 路径段统一替换为 `Tenants.namespace`（缺失回退 `tenant_name`），与 `migrate-console-helm-release` / `migrate-console-cluster-extras` 既定行为一致
- region 错误一律抛 `RegionApiException`，由 `GlobalExceptionHandler` 自动映射；不在 service 层硬编码中文 `msg_show`

### 接线 6-step 状态机内部 region 调用

不替换 controller 类，**仅在既有 endpoint 内部** 注入 `ShareOperations` 调用，保留 URL 路径与请求/响应 shape：

- `ServiceShareController.addEvent`（POST `/teams/{team_name}/share/{share_id}/events/{event_id}`）—— 改为：本地 `ServiceShareRecordEvent` 落库 → 调 `shareOps.shareService(...)` → 把 region 返回的 `region_share_id` / `event_id` / `image_name` / `slug_path` 回填到 event 行 → `event_status=start`；与 rainbond `share_services.py:592 sync_event` 行为一致
- `ServiceShareController.pluginEvent`（POST `.../events/{event_id}/plugin`）—— 改为：调 `shareOps.sharePlugin(...)` → 把 region 返回 `region_share_id` 回填本地（沿用 `ServiceShareRecordEvent` 复用 `region_share_id` 字段，与 rainbond `share_services.py:665 sync_service_plugin_event` 行为一致）
- 新增 `ServiceShareController.eventStatus`（GET `.../events/{event_id}/status`）—— 调 `shareOps.getShareServiceResult(...)` 拉 region 当前 `status`，刷新本地 `event_status`；对齐 rainbond `share_services.py:703 get_sync_event_result`
- `PluginShareController.addEvent`（POST `/teams/{team_name}/plugin-share/{share_id}/events/{event_id}`）—— 同理调 `shareOps.sharePlugin(...)` 推进 region 端
- 新增 `PluginShareController.eventStatus`（GET `.../plugin-share/{share_id}/events/{event_id}/status`）—— 调 `shareOps.getSharePluginResult(...)` 刷新本地 `event_status`
- `ServiceShareController.complete` 在最终步把"全部 event_status == success"写入 `is_success=true / status=1 / step=5`；任一 event 仍 `failure` 时不允许 complete（HTTP 409）

### 新增独立 controller 端点（2 个）

- `ServicePublishStatusController`（新建）GET `/console/teams/{team_name}/apps/{service_alias}/publish/status?service_key=&app_version=` —— 调 `getServicePublishStatus`，UI 在分享前置校验"该 service_key + version 是否已发布"
- `AppReleasesController`（新建）GET `/console/teams/{team_name}/groups/{group_id}/releases` —— 调 `listAppReleases(regionName, teamName, region_app_id)`；`region_app_id` 由 `ServiceGroup.regionAppId` 字段读取，UI 用于"分享发布历史"页面

### 不在本 change 内（明确推迟）

- 整组应用快照 / app_template 序列化（`share_services.py:531-720` 中的 `app_template / apps[].service_image / share_slug_path` 入库逻辑）—— 由 `migrate-console-app-import-export` 子 change 落地（rainbond `regionapi.py:export_app / import_app` 域）
- `complete` 阶段把 `RainbondCenterApp` 标记为 publish 完成（涉及应用市场跨表事务）—— 沿用现有本地占位
- 云市授权（403 → `cloud no permission`）—— 留给独立 marketplace OAuth 子 change
- 应用快照 / 模板回滚 / parent_id 升级链 —— 属 `migrate-console-app-upgrade` 范畴

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "应用分享 6-step 状态机 region 接线"。覆盖 7 个 region method 的 URL 锚点 / `ShareOperations` 接口契约 / 6-step 状态机各步对应的 region 调用注入点 / cloud share vs share_service vs share_plugin 的语义边界 / `list_app_releases` 的"分享发布历史"归属 / 与 `app-market` / `app-import-export` 子 change 的解耦边界。

## Impact

- **代码新增**：
  - 接口：`modules/appmarket/share/api/ShareOperations.java`（7 method，业务自治接口）
  - 实现：`modules/appmarket/share/api/ShareOperationsImpl.java`（`@Primary @Service`）
  - controller：新建 `ServicePublishStatusController` / `AppReleasesController`（2 个端点）；`ServiceShareController` / `PluginShareController` 既有类内修改（不替换路径）
  - service：新增 `ShareEventDispatcher`（封装"region 调用 + 本地事件回填"的事务边界，给 controller 复用）
  - entity：**无新增**（复用 `ServiceShareRecord` / `ServiceShareRecordEvent` / `TenantPluginShare` / `PluginShareRecordEvent`）
  - 单测：`ShareOperationsImplTest`（7 method × happy + 5xx 透传 + namespace fallback 共 ≥15 用例）
  - 集成测试：`ShareLifecycleIntegrationTest`（6-step happy / 失败回滚 / plugin 分享 / cloud share / 5xx 透传 / publish status / releases 列表 共 ≥7 用例）
- **数据库**：无变更（4 张本地表 entity 已存在，schema 由 rainbond Django migrations 拥有；本 change 仅 INSERT / UPDATE region_share_id / event_id / event_status 字段）
- **依赖**：不引入新 maven 依赖
- **跨 change 衔接**：
  - 与 `migrate-console-app-market` 共享 entity 与 repository（不动 schema），仅在内部状态机注入 region 调用
  - 与 `migrate-console-application-core` 共享 `ServiceGroup.regionAppId` 字段（用于 `list_app_releases`）
  - 与 `migrate-console-plugin` 共享 `TenantPlugin` / `TenantPluginShare` entity；plugin 分享流程在 `PluginShareController` 内推进
  - 后续 `migrate-console-app-import-export` 子 change 落地 app_template 序列化时复用本 change 的 `region_share_id` 关联
- **不影响**：rainbond-console（仍可独立跑 7070；同库共享 `service_share_record` 表，本 change 写入字段集与 rainbond Python 写入字段集 1:1 对齐）、其他已迁移 change
- **路径变量**：`{team_name}` / `{group_id}` / `{share_id}` / `{event_id}` / `{service_alias}` / `{plugin_id}` 全部 snake_case，与 rainbond `console/urls/__init__.py` 严格一致；trailing slash 双声明
