# Tasks — migrate-console-app-share

## 1. 校验既有骨架与依赖

- [ ] 1.1 确认 `cn.kuship.console.modules.appmarket.share.entity.ServiceShareRecord`（19 列）/ `ServiceShareRecordEvent` / `cn.kuship.console.modules.plugin.team.entity.TenantPluginShare`（17 列）/ `PluginShareRecordEvent` 4 张 entity 已存在且字段集与 rainbond 对齐（`region_share_id` / `event_id` / `event_status` 关键列存在）
- [ ] 1.2 确认 `ServiceShareRecordRepository` / `ServiceShareRecordEventRepository` / `TenantPluginShareRepository` / `PluginShareRecordEventRepository` 已存在；本 change 在此基础上不再新增 repository
- [ ] 1.3 确认 `ServiceShareController`（13 endpoint）/ `PluginShareController`（5 endpoint）已存在；本 change 在既有 controller 类内修改，不替换文件
- [ ] 1.4 确认 `RegionClientFactory` / `RegionApiException` / `RegionErrorMsgEnricher` / `GlobalExceptionHandler` 已落地（来自 `migrate-console-region-client` / `migrate-console-response-contract`），本 change 直接复用
- [ ] 1.5 探测 region 端：用本地 8080 console 已注入的 RegionClient，curl 7 个 URL 验证 region Go 端真实存在与响应 shape（**实施期推迟到 task §7 联动验证**：实施环境无在线 region 实例，先按 rainbond Python 路径默认实现，5xx 时 task §5 集成测试用 mock 兜底）：
  - `POST /v2/tenants/<ns>/cloud-share` → 期望 200 + bean
  - `POST /v2/tenants/<ns>/services/<alias>/share` → 期望 200 + bean.share_id / event_id / image_name
  - `GET /v2/tenants/<ns>/services/<alias>/share/<region_share_id>` → 期望 200 + bean.status
  - `POST /v2/tenants/<ns>/plugins/<plugin_id>/share` → 期望 200 + bean.share_id
  - `GET /v2/tenants/<ns>/plugins/<plugin_id>/share/<region_share_id>` → 期望 200 + bean.status
  - `GET /v2/builder/publish/service/<service_key>/version/<app_version>` → 期望 200 或 404
  - `GET /v2/tenants/<ns>/apps/<region_app_id>/releases` → 期望 200 + list

## 2. 新建 ShareOperations 接口与实现

- [ ] 2.1 新建 `cn.kuship.console.modules.appmarket.share.api.ShareOperations.java` 接口，声明 7 method（与 design.md "Region API URL 表"严格对齐），每个 method 加 default 占位抛 `UnsupportedOperationException("not yet implemented; will be filled in by migrate-console-app-share")`
- [ ] 2.2 新建 `cn.kuship.console.modules.appmarket.share.api.ShareOperationsImpl.java`（`@Primary @Service`），注入 `RegionClientFactory` / `TenantsRepository` / `tools.jackson.databind.ObjectMapper` / `RegionApiResponseProcessor`
- [ ] 2.3 实现 `shareCloudService(regionName, tenantName, body)`：
  - 通过 `tenantsRepo.findByTenantName(tenantName)` 取 namespace（缺失回退 tenant_name）
  - URL = `/v2/tenants/{namespace}/cloud-share`
  - POST + JSON body，`processor.extractBean(resp, Map.class, ...)`
- [ ] 2.4 实现 `shareService(regionName, tenantName, serviceAlias, body)`：
  - URL = `/v2/tenants/{namespace}/services/{service_alias}/share`
  - POST + JSON body
  - 响应 `bean.share_id` / `bean.event_id` / `bean.image_name` / `bean.slug_path` 完整透传（不做字段过滤）
- [ ] 2.5 实现 `getShareServiceResult(regionName, tenantName, serviceAlias, regionShareId)`：
  - URL = `/v2/tenants/{namespace}/services/{service_alias}/share/{region_share_id}`
  - GET → `bean.status` 透传
- [ ] 2.6 实现 `sharePlugin(regionName, tenantName, pluginId, body)`：
  - URL = `/v2/tenants/{namespace}/plugins/{plugin_id}/share`
  - POST 同 2.4 模式
- [ ] 2.7 实现 `getSharePluginResult(regionName, tenantName, pluginId, regionShareId)`：
  - URL = `/v2/tenants/{namespace}/plugins/{plugin_id}/share/{region_share_id}`
  - GET 同 2.5 模式
- [ ] 2.8 实现 `getServicePublishStatus(regionName, tenantName, serviceKey, appVersion)`：
  - **特殊**：URL **不含** `{namespace}` 段（rainbond Python 端 `regionapi.py:1331-1339` 仅调 `__get_region_access_info` 不调 `__get_tenant_region_info`）
  - URL = `/v2/builder/publish/service/{service_key}/version/{app_version}`，`service_key` / `app_version` URL encode
  - GET → bean 透传
- [ ] 2.9 实现 `listAppReleases(regionName, tenantName, regionAppId)`：
  - URL = `/v2/tenants/{namespace}/apps/{region_app_id}/releases`
  - GET → 取 `body.list` 返回（rainbond Python 端直接 `return body["list"]`，本 change 在 controller 层包成 `data.list`）
- [ ] 2.10 单测 `ShareOperationsImplTest` 至少 15 用例：
  - 7 method × 1 happy + 1 region 5xx 透传（共 14 用例）
  - 1 namespace fallback 用例（`Tenants.namespace == null` → URL 落到 tenant_name）
  - `MockRestServiceServer` 断言 URL / HTTP method / body JSON 形状
  - 至少 1 个频率限制（429 → `RegionApiFrequentException`）用例

## 3. 接线 ServiceShareController 内部 region 调用

- [ ] 3.1 在 `ServiceShareController` 注入 `ShareOperations` / `TenantsRepository` / `TenantServiceRepository`（取 service_alias 对应 tenant_id 与 region）
- [ ] 3.2 改造 `addEvent`（POST `.../share/{share_id}/events/{event_id}`）：
  - 保留本地 `ServiceShareRecordEvent` INSERT（existing logic）
  - 在 `@Transactional` 内追加调用 `shareOps.shareService(regionName, teamName, body.service_alias, regionBody)` —— `regionBody` 含 `service_key` / `app_version` / `deploy_version` / `event_id` / `share_user` / `share_scope` / `image_info` / `slug_info`（与 rainbond `share_services.py:583` 字段集对齐）
  - region 返回后回填 `event.regionShareId = bean.share_id`、`event.eventId = bean.event_id`、`event.eventStatus = "start"`、`event.updateTime = now`
  - region 失败 → Spring 自动回滚事务（删除已 INSERT 的 event 行）
- [ ] 3.3 改造 `pluginEvent`（POST `.../share/{share_id}/events/{event_id}/plugin`）：
  - 之前是 `return GeneralMessage.ok()` 占位
  - 改为：调用 `shareOps.sharePlugin(regionName, teamName, body.plugin_id, regionBody)` —— `regionBody` 含 `plugin_id` / `plugin_version` / `plugin_key` / `event_id` / `share_user` / `share_scope` / `image_info`（对齐 `share_services.py:660`）
  - 回填 `event.regionShareId = bean.share_id` / `event.eventId = bean.event_id` / `event.eventStatus = "start"`
  - 沿用既有 `ServiceShareRecordEvent` 表（rainbond 端插件分享在服务分享 record 内复用同一表，本 change 同）
- [ ] 3.4 新增 `eventStatus`（GET `.../share/{share_id}/events/{event_id}/status`）：
  - 从 `eventRepo.findByEventId(...)` 取 event 行 → 读 `regionShareId`
  - `regionShareId == null` → 返 200 + `data.bean = {event_status: "pending"}`，不调 region
  - 否则调 `shareOps.getShareServiceResult(regionName, teamName, event.serviceAlias, event.regionShareId)` → 把 `bean.status` 写回 `event.eventStatus` + 持久化 → 返 200 + `data.bean.status`
  - 路径同时声明 trailing slash 兼容
- [ ] 3.5 改造 `complete`（POST `.../share/{share_id}/complete`）：
  - 校验全部 event_status == "success"；任一 ∈ {"failure", "fail", "error"} → `ServiceHandleException(409, "share not all success", "存在失败事件，请放弃后重试")`
  - 仍未到 success（still "running" / "start"）→ `ServiceHandleException(409, "share not finished", "分享尚未完成")`
  - 全部 success → `r.status=1 / r.step=5 / r.isSuccess=true / r.updateTime=now`，持久化
  - **不**调 region API（rainbond Python 端 complete 只翻本地 status，与 region 任务异步 fire-and-forget 模式一致）

## 4. 接线 PluginShareController 内部 region 调用

- [ ] 4.1 在 `PluginShareController` 注入 `ShareOperations`
- [ ] 4.2 改造 `addEvent`（POST `.../plugin-share/{share_id}/events/{event_id}`）：
  - 保留本地 `PluginShareRecordEvent` INSERT
  - 追加 `shareOps.sharePlugin(regionName, teamName, share.originPluginId, regionBody)` 调用
  - `regionBody` = `{plugin_id, plugin_version, plugin_key, event_id, share_user, share_scope, image_info}`，对齐 `market_plugin_service.py:280-300`
  - 回填 `event.regionShareId = bean.share_id` / `event.eventId = bean.event_id` / `event.eventStatus = "start"`
  - region 失败 → 事务回滚
- [ ] 4.3 新增 `eventStatus`（GET `.../plugin-share/{share_id}/events/{event_id}/status`）：
  - 同 task 3.4 模式，调 `shareOps.getSharePluginResult(...)` 刷本地 `event_status`
  - `regionShareId == null` 走 pending 短路
- [ ] 4.4 `complete`（POST `.../plugin-share/{share_id}/complete`）：
  - rainbond plugin share 历史无独立 status 列，沿用既有"shareVersion 末尾追加 `_COMPLETE`"语义
  - 校验：全部 event_status == "success" 才允许追加 `_COMPLETE`，否则 409
  - 不调 region

## 5. 新建独立 controller endpoint

- [ ] 5.1 新建 `cn.kuship.console.modules.appmarket.share.controller.ServicePublishStatusController.java`：
  - `@GetMapping({"/console/teams/{team_name}/apps/{service_alias}/publish/status", ".../publish/status/"})`
  - `@RequestParam("service_key") String serviceKey` + `@RequestParam("app_version") String appVersion`
  - 调 `shareOps.getServicePublishStatus(regionName, teamName, serviceKey, appVersion)`
  - regionName 通过 `tenantServiceRepo.findByServiceAlias(...)`.serviceRegion 取（避免在 path 中暴露 region）
  - `@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)`
- [ ] 5.2 新建 `cn.kuship.console.modules.appmarket.share.controller.AppReleasesController.java`：
  - `@GetMapping({"/console/teams/{team_name}/groups/{group_id}/releases", ".../releases/"})`
  - 通过 `serviceGroupRepo.findByGroupId(groupId)` 取 `serviceGroup.regionAppId`（缺失或为空 → 返 200 + `data.list = []`）
  - 同时取 `serviceGroup.region` 作为 regionName
  - 调 `shareOps.listAppReleases(regionName, teamName, regionAppId)` → return 直接 list（advice 自动包成 `data.list`）
  - `@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)`
- [ ] 5.3 全部 controller 路径变量统一 snake_case；trailing slash 双声明；不显式 ApiResult 包装（advice 自动）

## 6. SecurityConfig（如需）

- [ ] 6.1 8 个端点（含既有 6 + 新 2）全部走默认 JWT 鉴权链，**无需** permitAll；本 change 不修改 SecurityConfig
- [ ] 6.2 `@RequirePerm` / `@RequireEnterpriseAdmin` 切面已在 `migrate-console-account-team` 落地，本 change 直接使用注解；不动 PermAspect

## 7. 集成测试

合并到 `ShareLifecycleIntegrationTest` 类（@SpringBootTest + @ActiveProfiles({"local","contract-test"}) + @MockitoBean ShareOperations + JdbcTemplate seed），降低 fixture 重复成本：

- [ ] 7.1 `service_share_full_lifecycle_happy`：6-step 全程
  - POST `/share/record` → step=0
  - POST `/share/record/{rid}/info` → step=1（本地）
  - POST `/share/{share_id}/info` → step=2（本地）
  - POST `/share/{share_id}/events/{event_id}` → mock `shareService` 返 `bean={share_id:"rsid", event_id:"eid", image_name:"img"}` → 断言 event 行 `regionShareId="rsid"` / `eventStatus="start"`
  - GET `/share/{share_id}/events/{event_id}/status` → mock `getShareServiceResult` 返 `bean.status="success"` → 断言本地 event_status 更新为 success
  - POST `/share/{share_id}/complete` → 断言 record `status=1 / step=5 / is_success=true`
- [ ] 7.2 `service_share_event_addition_rollback_on_region_5xx`：
  - POST `/share/{share_id}/events/{event_id}` 时 mock `shareService` 抛 `RegionApiException(503, "internal", "数据中心分享错误")`
  - 断言响应 503 + msg_show=数据中心分享错误（透传）
  - 断言 `service_share_record_event` 表无新行（事务回滚）
- [ ] 7.3 `plugin_share_event_persists_region_share_id`：
  - POST `/plugin-share/{share_id}/events/{event_id}` → mock `sharePlugin` 返 `bean.share_id="psid"` → 断言 `PluginShareRecordEvent.regionShareId="psid"`
  - GET `/plugin-share/{share_id}/events/{event_id}/status` → mock `getSharePluginResult` 返 success → 断言本地 event_status=success
- [ ] 7.4 `cloud_share_interface_dispatchable_without_controller`：
  - 通过 `@Autowired ShareOperations` 直接调 `shareCloudService(...)` → `MockRestServiceServer` 断言 URL `/v2/tenants/<ns>/cloud-share`
  - 验证 controller 层未触达此 method（grep `ServiceShareController` 字节码确认无 `shareCloudService` 调用）
- [ ] 7.5 `share_service_result_polling_status_progression`：
  - 状态从 "start" → "running" → "success" 三轮轮询，断言每次 GET `/events/{event_id}/status` 后本地 event_status 同步推进
- [ ] 7.6 `publish_status_query_happy`：
  - GET `/apps/{service_alias}/publish/status?service_key=foo&app_version=1.0`
  - mock `getServicePublishStatus` 返 `bean.status="published"` → 断言响应 200 + `data.bean.status="published"`
- [ ] 7.7 `app_releases_list_resolves_region_app_id`：
  - seed `service_group` 行 `region_app_id="rid-32-char"`、`region="rainbond"`
  - GET `/groups/{group_id}/releases`
  - mock `listAppReleases` 返 `["v1.0", "v2.0"]` → 断言响应 200 + `data.list = ["v1.0","v2.0"]`
- [ ] 7.8 `app_releases_returns_empty_when_region_app_id_missing`：
  - seed `service_group` 行 `region_app_id=null`
  - GET `/groups/{group_id}/releases` → 200 + `data.list = []`，**未调用 region**（mock `shareOps.listAppReleases` 用 `verify(..., never())`）
- [ ] 7.9 `complete_rejected_when_event_failed`：
  - 构造 1 个 event_status=failure
  - POST `/share/{share_id}/complete` → 409 + msg_show=存在失败事件，请放弃后重试
- [ ] 7.10 `region_429_propagates_msg_show`：
  - mock `shareService` 抛 `RegionApiFrequentException(429, "frequent", "操作过于频繁，请稍后再试")`
  - 断言响应 429 + msg_show=操作过于频繁，请稍后再试
- [ ] 7.11 `share_service_namespace_fallback`：
  - seed `Tenants.namespace=null` 用例 → `MockRestServiceServer` 断言 URL 段落到 `tenant_name` 而非 namespace

## 8. 文档与归档

- [ ] 8.1 更新 `kuship-console/CLAUDE.md` 在"应用市场（migrate-console-app-market）"段后追加"应用分享 6-step 状态机 region 接线（migrate-console-app-share）"段：
  - 列 7 region method 路径表（method / HTTP / URL / 锚点）
  - 列 6-step 状态机各步对应 region 调用注入点表
  - 列 2 个新 controller 端点（publish/status / releases）
  - 标注与 `migrate-console-app-import-export` 子 change 的 app_template 序列化解耦边界
  - 同步更新接口表："App 分享流程"行标 7/7 完成
- [ ] 8.2 路线图 `migrate-region-coverage-roadmap` 的 Requirement 表中把 "App 分享流程" 行（# 16）从 0 → 7 标注为已完成（归档时执行）
- [ ] 8.3 记录 1.5 探测结果的最终 region URL 与响应 shape 到 `design.md` "Region API URL 表" 下方"实施期探测结果"小节，便于后续 `migrate-console-app-import-export` 子 change 复用 region_share_id 关联

## 9. 编译 / 重启 / 联动验证

- [ ] 9.1 `cd kuship-console && mvn -DskipTests package` 通过；`mvn test -Dtest=ShareOperationsImplTest,ShareLifecycleIntegrationTest` 至少 22 用例（15 单测 + 7 集成）全过
- [ ] 9.2 重启 console；用 `kuship-ui` 完整跑分享流程（**需用户本地起 console + region + ui 联动**）：
  - 应用分享：建 record → 推 info → 添加 event → 轮询 status → complete
  - 验证 `service_share_record_event` 表 `region_share_id` 列已被回填（rainbond 真实 region 任务 ID）
- [ ] 9.3 `curl ... /console/teams/default/groups/{group_id}/releases` 返 200 + `data.list`（**需用户联动**）
- [ ] 9.4 `curl ... /console/teams/default/apps/{service_alias}/publish/status?service_key=foo&app_version=1.0` 返 200（**需用户联动**）
- [ ] 9.5 插件分享路径联动验证（**需用户联动**）：
  - POST `/plugin-share/{share_id}/events/{event_id}` → 验证 `plugin_share_record_event.region_share_id` 写入
  - GET `/plugin-share/{share_id}/events/{event_id}/status` → 拉 region status 同步到本地
- [ ] 9.6 失败回滚联动（**需用户联动**）：
  - 主动拔掉 region 网络（或用 `kuship.region.timeout-seconds=1` + 阻塞 region 端） → POST event → 验证 503 透传 + 本地 event 表无新行
