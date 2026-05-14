## ADDED Requirements

### Requirement: 应用分享 6-step 状态机 region 接线

kuship-console SHALL 落地 `cn.kuship.console.modules.appmarket.share.api.ShareOperations` 接口（7 个 region method：`shareCloudService` / `shareService` / `getShareServiceResult` / `sharePlugin` / `getSharePluginResult` / `getServicePublishStatus` / `listAppReleases`）与 `ShareOperationsImpl @Primary @Service` 实现，并在既有 `ServiceShareController`（13 endpoint）与 `PluginShareController`（5 endpoint）的 6-step / 3-status 状态机内部把这些 region 调用接线进去；新增 `ServicePublishStatusController` / `AppReleasesController` 共 2 个 controller endpoint 暴露给 UI 用于"分享前置校验"与"分享发布历史"。本 Requirement 同时锁定与后续 `migrate-console-app-import-export` 子 change 的解耦边界：本 change 仅负责 region 调用接线与 region_share_id 持久化，不做 app_template 序列化 / RainbondCenterApp publish 标记 / 整组应用快照；后续子 change SHALL 复用本 change 写入的 `region_share_id` 字段做 app_template 关联，不重写本 change 的 region 调用层。

业务规则：

- 7 个 region method MUST 把路径段中的 `tenant_name`（rainbond Python 端 `region_tenant_name`）替换为 `Tenants.namespace`（缺失时回退 `tenant_name`），与 `migrate-console-helm-release` / `migrate-console-cluster-extras` / `migrate-console-third-party-runtime` 的 namespace 解析行为一致
- `getServicePublishStatus` 路径 MUST 不含 `{namespace}` 段（rainbond Python `regionapi.py:1331-1339` 仅调 `__get_region_access_info` 不调 `__get_tenant_region_info`），是 7 个 method 中唯一例外
- `ServiceShareController.addEvent` MUST 在 `@Transactional` 内：先 INSERT `ServiceShareRecordEvent` → 调 `shareService(...)` → 把 region 返回的 `bean.share_id` / `bean.event_id` / `bean.image_name` / `bean.slug_path` 回填到 event 行，并把 `event_status` 设为 `"start"`；region 失败 MUST 触发事务回滚（删除已 INSERT 的 event 行），与 rainbond `share_services.py:592 sync_event` 行为一致
- `ServiceShareController.pluginEvent` MUST 调 `sharePlugin(...)` 并回填 `regionShareId` / `eventId` / `eventStatus`；占位的 `return GeneralMessage.ok()` MUST 被替换
- `ServiceShareController.eventStatus`（GET 新增端点 `.../events/{event_id}/status`）MUST 调 `getShareServiceResult(...)` 拉 region 当前 `bean.status` 写回本地 `event_status`；`region_share_id == null` 时 MUST 返 200 + `data.bean.event_status="pending"`，不调 region
- `PluginShareController.addEvent` MUST 调 `sharePlugin(...)` 并回填 `PluginShareRecordEvent.regionShareId` / `eventId` / `eventStatus`，事务回滚同上
- `PluginShareController.eventStatus`（GET 新增端点 `.../plugin-share/{share_id}/events/{event_id}/status`）MUST 调 `getSharePluginResult(...)` 同步 region 状态到本地
- `ServiceShareController.complete` MUST 校验全部 event_status == "success" 才允许翻 `record.status=1 / step=5 / is_success=true`；任一 event_status 为 failure / fail / error MUST 抛 `ServiceHandleException(409, "share not all success", "存在失败事件，请放弃后重试")`；任一仍为 running / start MUST 抛 `ServiceHandleException(409, "share not finished", "分享尚未完成")`；complete MUST NOT 调用任何 region API（rainbond Python 端 complete 仅本地翻 status，与 region 任务 fire-and-forget 模式一致）
- `ServicePublishStatusController` GET `/console/teams/{team_name}/apps/{service_alias}/publish/status?service_key=&app_version=` MUST 通过 `tenantServiceRepo.findByServiceAlias(...)` 取 `serviceRegion` 作为 regionName（避免在 path 中暴露 region），调 `getServicePublishStatus(...)` 透传 region 响应
- `AppReleasesController` GET `/console/teams/{team_name}/groups/{group_id}/releases` MUST 通过 `serviceGroupRepo.findByGroupId(groupId)` 取 `regionAppId` + `region`；`regionAppId` 缺失或为空 MUST 返 200 + `data.list = []`（rainbond Python 行为），不抛异常、不调 region
- `shareCloudService` 接口 MUST 保留并可由 service 层注入调用，但 `ServiceShareController` 在本 change 内 MUST NOT 主动调用它（云市分享路径属 v3.5 前历史路径，留给后续 marketplace OAuth 子 change 接入）
- 8 个端点（既有 6 + 新 2）MUST 全部走默认 JWT 鉴权链，不进 permitAll；`ServicePublishStatusController` / `AppReleasesController` MUST 加 `@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)`
- region 异常 MUST 透传 `RegionApiException` 子类（含 `RegionApiFrequentException` 429 / 普通 4xx 5xx），由 `GlobalExceptionHandler` 自动映射为 general_message 形状；`msg_show` 优先用 region 自带的，缺失时由 `RegionErrorMsgEnricher` 兜底；service / controller 层 MUST NOT 硬编码中文 msg_show
- 4 张本地表（`service_share_record` / `service_share_record_event` / `tenant_plugin_share` / `plugin_share_record_event`）schema MUST 不被本 change 修改（rainbond Django migrations 拥有 schema 演进权）；本 change 仅 INSERT / UPDATE 既有列

#### Scenario: 服务分享 6-step 完整 happy path

- **GIVEN** team `default` 已建 share record（step=0），`info` 步推进到 step=2，`Tenants.namespace="ns-default"`
- **WHEN** `POST /console/teams/default/share/{share_id}/events/{event_id}`，body 含 `service_key="key1"` / `service_alias="svc-alias"` / `app_version="1.0"`
- **THEN** kuship 调 region `POST /v2/tenants/ns-default/services/svc-alias/share`，body 含 `service_key` / `app_version` / `event_id` / `share_user` / `share_scope` / `image_info` / `slug_info`
- **AND** region 返 200 + `bean={share_id:"rsid-32", event_id:"eid-32", image_name:"img:1.0"}`
- **AND** 本地 `service_share_record_event` 行 `region_share_id="rsid-32"` / `event_id="eid-32"` / `event_status="start"`
- **WHEN** `GET /console/teams/default/share/{share_id}/events/{event_id}/status`
- **THEN** kuship 调 region `GET /v2/tenants/ns-default/services/svc-alias/share/rsid-32`，region 返 `bean.status="success"`
- **AND** 本地 `event_status` 更新为 `success`
- **WHEN** `POST /console/teams/default/share/{share_id}/complete`
- **THEN** 响应 200 + `data.bean` 含 `status=1` / `step=5` / `is_success=true`
- **AND** 整个 complete 阶段未调用任何 region API

#### Scenario: 服务分享事件触发时 region 5xx 触发事务回滚

- **GIVEN** team `default` 当前 share record step=2，`service_share_record_event` 表当前空
- **WHEN** `POST /console/teams/default/share/{share_id}/events/{event_id}`，region 端 `POST /v2/tenants/ns-default/services/svc-alias/share` 返 503
- **THEN** 响应 503 + general_message 形状（msg / msg_show 来自 region）
- **AND** `service_share_record_event` 表无新行（事务回滚已 INSERT 的 event 行）
- **AND** `service_share_record.step` 未推进（仍为 2）

#### Scenario: 插件分享事件 region_share_id 持久化

- **GIVEN** team `default` 已建 plugin share record（plugin_id="pid-uuid"），`Tenants.namespace="ns-default"`
- **WHEN** `POST /console/teams/default/plugin-share/{share_id}/events/{event_id}`，body 含 `plugin_id="pid-uuid"` / `plugin_version="0.1"` / `plugin_key="kuship-test"`
- **THEN** kuship 调 region `POST /v2/tenants/ns-default/plugins/pid-uuid/share`
- **AND** region 返 `bean={share_id:"psid-32", event_id:"peid-32"}`
- **AND** 本地 `plugin_share_record_event` 行 `region_share_id="psid-32"` / `event_id="peid-32"` / `event_status="start"`
- **WHEN** `GET /console/teams/default/plugin-share/{share_id}/events/{event_id}/status`
- **THEN** kuship 调 region `GET /v2/tenants/ns-default/plugins/pid-uuid/share/psid-32`，region 返 `bean.status="success"`
- **AND** 本地 `plugin_share_record_event.event_status="success"`

#### Scenario: 云市分享接口可被独立调用且不被 controller 触达

- **GIVEN** service 层注入 `ShareOperations`
- **WHEN** 直接调 `shareOps.shareCloudService("rainbond", "default", body)`
- **THEN** kuship 调 region `POST /v2/tenants/ns-default/cloud-share`，body 透传
- **AND** 响应 200 + bean 透传
- **AND** `ServiceShareController` 类字节码内 MUST NOT 含 `shareCloudService` 调用引用（保留为 marketplace OAuth 子 change 后续接入路径）

#### Scenario: complete 阶段拒绝 event_status 含失败的 record

- **GIVEN** team `default` 的 share record 已推进到 step=4，`service_share_record_event` 表 3 行其中 1 行 `event_status="failure"`
- **WHEN** `POST /console/teams/default/share/{share_id}/complete`
- **THEN** 响应 409 + `msg="share not all success"` + `msg_show="存在失败事件，请放弃后重试"`
- **AND** record `status` 未翻为 1（仍为 0）
- **AND** 整个 complete 阶段未调用任何 region API

#### Scenario: 分享发布历史按 region_app_id 列出

- **GIVEN** team `default` 的 `service_group.group_id=42` 行 `region_app_id="rapp-32"` / `region="rainbond"`
- **WHEN** `GET /console/teams/default/groups/42/releases`
- **THEN** kuship 调 region `GET /v2/tenants/ns-default/apps/rapp-32/releases`
- **AND** region 返 `body.list=["v1.0","v2.0"]`
- **AND** 响应 200 + `data.list=["v1.0","v2.0"]`

#### Scenario: 分享发布历史在 region_app_id 缺失时返空列表不调 region

- **GIVEN** team `default` 的 `service_group.group_id=43` 行 `region_app_id` 为 null（早期未走 region 同步的应用）
- **WHEN** `GET /console/teams/default/groups/43/releases`
- **THEN** 响应 200 + `data.list=[]`
- **AND** 未调用任何 region API（mock `shareOps.listAppReleases` 用 `verify(..., never())` 验证）

#### Scenario: 发布前置校验透传 region 状态

- **GIVEN** team `default` 的 service_alias=svc-alias 对应 region=rainbond
- **WHEN** `GET /console/teams/default/apps/svc-alias/publish/status?service_key=key1&app_version=1.0`
- **THEN** kuship 调 region `GET /v2/builder/publish/service/key1/version/1.0`（**路径不含 namespace 段**）
- **AND** region 返 `bean.status="published"`
- **AND** 响应 200 + `data.bean.status="published"`

#### Scenario: region 频率限制错误透传 msg_show

- **GIVEN** team `default` 在分享高峰，region 端对 `POST .../services/{alias}/share` 返 429 + `{msg_show:"操作过于频繁，请稍后再试"}`
- **WHEN** `POST /console/teams/default/share/{share_id}/events/{event_id}`
- **THEN** kuship 抛 `RegionApiFrequentException(429,...)` → `GlobalExceptionHandler` 映射
- **AND** 响应 HTTP 状态码 429 + `msg="frequent"` + `msg_show="操作过于频繁，请稍后再试"`（透传 region）
- **AND** 本地 `service_share_record_event` 表无新行（事务回滚）

#### Scenario: namespace 缺失时 URL 回退到 tenant_name

- **GIVEN** team `default` 的 `Tenants.namespace == null`
- **WHEN** `POST /console/teams/default/share/{share_id}/events/{event_id}`，触发 `shareService(...)` 调用
- **THEN** kuship 调 region URL `POST /v2/tenants/default/services/svc-alias/share`（路径段落到 tenant_name=default）
- **AND** 响应 200（namespace fallback 不影响 happy path）

#### Scenario: 与 app-import-export 子 change 的边界

- **GIVEN** 本 change 已落地，`service_share_record_event.region_share_id` 字段已被本 change 写入
- **WHEN** `migrate-console-app-import-export` 子 change 后续要在 share record 上加 app_template 序列化
- **THEN** 该子 change SHALL 在新 service / 新 controller 内读取 `region_share_id` 与 region 端建立关联，不替换本 change 的 `ShareOperations` 接口或 `ShareOperationsImpl` 实现
- **AND** 该子 change SHALL NOT 修改 `ServiceShareController.addEvent` / `pluginEvent` / `eventStatus` / `complete` 内的 region 调用接线
- **AND** 整组应用快照 / RainbondCenterApp publish 完成标记的跨表事务由后续子 change 自行承担，不污染本 change 的 region 调用层
