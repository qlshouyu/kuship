# Design — migrate-console-app-share

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是 **P1 #2**（应用分享流程 / 6-step 状态机），估计 method 数 **7**，工作量约 4-5 天，远低于决策 2 中的 ≤ 30 method 上限；归档时反向更新路线表第 16 行（"App 分享流程"）从 0 → 7（即"已通"），同时把 P1 子 change 列表中本行标记为已完成。

依赖：无（`migrate-console-app-market` / `migrate-console-plugin` 已落地 4 张本地表 entity / repository / 6-step 状态机骨架；本 change 不动 schema、不动 controller URL）。本 change 完成后 `migrate-console-app-import-export` 可以复用 `region_share_id` 字段做 app_template 关联，但二者无强依赖。

## Region API URL 表

| method                                            | HTTP   | 路径                                                                            | rainbond 锚点                                          |
|---------------------------------------------------|--------|---------------------------------------------------------------------------------|---------------------------------------------------------|
| shareCloudService(rn, tn, body)                   | POST   | `/v2/tenants/{namespace}/cloud-share`                                           | `regionapi.py:975 share_clound_service`                 |
| shareService(rn, tn, alias, body)                 | POST   | `/v2/tenants/{namespace}/services/{service_alias}/share`                        | `regionapi.py:987 share_service`                        |
| getShareServiceResult(rn, tn, alias, regionShareId)| GET   | `/v2/tenants/{namespace}/services/{service_alias}/share/{region_share_id}`      | `regionapi.py:996 share_service_result`                 |
| sharePlugin(rn, tn, pluginId, body)               | POST   | `/v2/tenants/{namespace}/plugins/{plugin_id}/share`                             | `regionapi.py:1006 share_plugin`                        |
| getSharePluginResult(rn, tn, pluginId, regionShareId)| GET | `/v2/tenants/{namespace}/plugins/{plugin_id}/share/{region_share_id}`           | `regionapi.py:1015 share_plugin_result`                 |
| getServicePublishStatus(rn, tn, serviceKey, version)| GET  | `/v2/builder/publish/service/{service_key}/version/{app_version}`               | `regionapi.py:1331 get_service_publish_status`          |
| listAppReleases(rn, tn, regionAppId)              | GET   | `/v2/tenants/{namespace}/apps/{region_app_id}/releases`                         | `regionapi.py:2389 list_app_releases`                   |

`{namespace}` 路径段：rainbond Python 端统一从 `__get_tenant_region_info(...).region_tenant_name` 取，对应 kuship `Tenants.namespace`；缺失时回退 `tenant_name`，与 `migrate-console-helm-release` / `migrate-console-cluster-extras` / `migrate-console-third-party-runtime` 行为一致。`getServicePublishStatus` 路径中**没有** `{namespace}` 段（rainbond Python `get_service_publish_status` 实现里只调 `__get_region_access_info`，未拼 tenant 段），是 7 个 method 中唯一例外。

## Controller 路径锚点

| Controller                       | path                                                                          | method | rainbond 锚点 / 备注                                  |
|----------------------------------|-------------------------------------------------------------------------------|--------|------------------------------------------------------|
| ServiceShareController（既有）    | `/console/teams/{team_name}/share/{share_id}/events/{event_id}`               | POST   | `urls.py:1180` `ServiceShareEventInfo` —— 本 change 在内部接线 region |
| ServiceShareController（既有）    | `/console/teams/{team_name}/share/{share_id}/events/{event_id}/plugin`        | POST   | `urls.py:1182` `ServicePluginShareEventInfo` —— 本 change 在内部接线 region |
| ServiceShareController（新增端点）| `/console/teams/{team_name}/share/{share_id}/events/{event_id}/status`        | GET    | rainbond 端是 `urls.py:1184` `ServiceShareEventList` 的 sync 模式（无独立 status 端点，本 change 拆分语义） |
| ServiceShareController（既有）    | `/console/teams/{team_name}/share/{share_id}/complete`                        | POST   | `urls.py:1185` `ServiceShareCompleteView` —— 本 change 在 complete 内校验"所有 event 都 success" |
| PluginShareController（既有）     | `/console/teams/{team_name}/plugin-share/{share_id}/events/{event_id}`        | POST   | rainbond 端 plugin share controller（`market_plugin_service.py`）—— 本 change 接线 region |
| PluginShareController（新增端点） | `/console/teams/{team_name}/plugin-share/{share_id}/events/{event_id}/status` | GET    | 复刻 `market_plugin_service.py:305 share_plugin_result` 的 console 暴露层 |
| ServicePublishStatusController（新）| `/console/teams/{team_name}/apps/{service_alias}/publish/status`              | GET    | rainbond 端无独立 console URL（直接走 region 透传），本 change 新建供 UI"分享前置校验"调用 |
| AppReleasesController（新）        | `/console/teams/{team_name}/groups/{group_id}/releases`                       | GET    | `console/services/application.py:19 list_app_releases` 的 console 暴露层 |

trailing slash 兼容沿用既定规则（每 endpoint 同时声明 `path` 与 `path/`）。

`ServicePublishStatusController` 的路径选 `/apps/{service_alias}/publish/status` 而非挂在 share 域下：rainbond Python 端 `get_service_publish_status` 是按 `service_key + app_version` 查（与 service_alias 间接关联），UI 调用点是分享前置校验，挂在 `/apps/*` 下与 `migrate-console-application-core` 已有命名空间一致；区别于 `/share/{share_id}/...`（share record 维度的状态查询）。

`AppReleasesController` 的 `{group_id}` 是 `service_group.group_id`（int），controller 内部要 `serviceGroupRepo.findByGroupId(...)` 取 `service_group.regionAppId`（32-char UUID）；rainbond Python 端 `application.py:19 list_app_releases` 同样做这一步映射。

## 决策 1 — 6-step 状态机各步与 region method 的对应关系

`service_share_record` 表的 `step / status` 是分享流程的核心状态机：`step ∈ {0,1,2,3,4,5}`、`status ∈ {0,1,2}`（0=进行中、1=完成、2=已放弃）。本 change 锁定每一步对应的 region 调用注入点（"画一张表"）：

| step | controller endpoint                     | 状态推进                          | region method 注入点                    | 锚点               |
|------|-----------------------------------------|-----------------------------------|----------------------------------------|--------------------|
| 0    | `POST .../share/record`                 | record 创建，本地落库              | （无 region 调用）                      | rainbond 同 step  |
| 1    | `POST .../share/record/{rid}/info`      | 选模板 / 版本号                    | （无 region 调用）                      | rainbond 同 step  |
| 2    | `POST .../share/{share_id}/info`        | 推 app_template + 选 scope         | （无 region 调用）                      | rainbond `share_services.py:1020 step=2` |
| 3    | `POST .../share/{share_id}/events/{event_id}` 与 `.../events/{event_id}/plugin` | **关键**：服务级 + 插件级事件同步 | `shareService(...)` / `sharePlugin(...)` | rainbond `share_services.py:592 sync_event` / `665 sync_service_plugin_event` |
| 4    | `GET .../events/{event_id}/status`（轮询） | event_status 由 region 推进       | `getShareServiceResult(...)` / `getSharePluginResult(...)` | rainbond `share_services.py:694,703 get_sync_event_result` |
| 5    | `POST .../share/{share_id}/complete`     | 校验全部 event=success → status=1 | （无新 region 调用，只校验本地 event_status） | rainbond `share_services.py:1172 step=3, status=1`（注：rainbond 用 step=3 标记最终态，本 change 复刻 entity 字段语义但 ServiceShareController.complete 使用 step=5 / status=1，与既有 controller 注释一致） |

**回滚路径**（status=2 已放弃）：

| 触发                                  | 状态推进              | region 调用                         | 说明                                |
|---------------------------------------|-----------------------|-------------------------------------|--------------------------------------|
| `POST .../share/{share_id}/giveup`     | status=2              | （无）                               | rainbond 同行为；不调 region 反向操作 |
| event_status=failure（region 端轮询返） | step 不前推；status 保持 0 | `getShareServiceResult(...)` 返 failure | UI 在 status 端点拿到 failure 后引导用户 giveup |

**关键不对称**：rainbond Python 端 `complete` 阶段不调任何 region API，仅本地翻 status；这是因为 region 端 share_service 任务是"投递即异步"模式，event 级别的成功 / 失败由 region 自己写到 event 表里，console 端只读不写。本 change 沿用此模式，complete 不调 region。

## 决策 2 — share_service vs share_clound_service vs share_plugin 的语义边界

rainbond `regionapi.py` 区分三种"分享"出口，本 change 的 `ShareOperations` 接口完整保留三个，分别对应 UI 三种用户场景：

| 分享类型           | region method            | 触发场景                             | 本地表                    | controller 入口        |
|--------------------|--------------------------|--------------------------------------|---------------------------|-------------------------|
| **服务分享**（主流）| `shareService`           | 团队内分享一个 service 到本地市场      | `service_share_record` + `service_share_record_event` | `ServiceShareController.addEvent` |
| **云市分享**（v3.5 前）| `shareCloudService`     | 直推到外部云市（goodrain.com）        | `service_share_record`（scope 含 `goodrain` 前缀） | 历史 controller，本 change 保留接线但不新增 endpoint |
| **插件分享**       | `sharePlugin`            | 团队内分享一个 plugin                 | `tenant_plugin_share` + `plugin_share_record_event` | `PluginShareController.addEvent` |

**决策**：
- `shareCloudService` 在 `ShareOperations` 接口中保留，但 ServiceShareController 不主动调用（rainbond 端 `share_services.py:592 sync_event` 也只调 `share_service`，云市分享走另一条 `share_clound_service` 路径，UI 端 v3.5 后已移除入口；本 change 只保接口，不接 controller，留给后续 marketplace OAuth 子 change 接入）。
- `shareService` 与 `sharePlugin` 是分享主流路径，`sync_event` 与 `sync_service_plugin_event` 在 rainbond Python 是两个分开的事务方法 —— 本 change 同样拆为两个 controller 入口（`ServiceShareController.addEvent` 处理服务、`ServiceShareController.pluginEvent` + `PluginShareController.addEvent` 处理插件），不做合并。
- `cloud_share` URL `/v2/tenants/{namespace}/cloud-share` 有别于 `share_service` 的 `/v2/tenants/{namespace}/services/{alias}/share`：前者无 service_alias 段，body 含完整应用模板（rainbond 端用于云市直推）；后者按 service 维度调用，body 仅含单个 service 的 share_scope / event_id / image_info。

**测试覆盖**：spec.md 至少 1 个 `#### Scenario: 云市分享接口可被独立调用` 用例验证 `shareCloudService` 接口可被 service 层注入并直发，即使本 change 不在 ServiceShareController 内主动调用它。

## 决策 3 — list_app_releases 的归属

`list_app_releases(region_name, tenant_name, app_id)` 在 rainbond Python 端是 `console/services/application.py:19` 的一个独立 helper，被分享发布历史页面 / 应用模板版本对比页面调用。其**域归属**有两种选择：

- (A) 放在 `migrate-console-application-core` 子 change 内（按 application 域）
- (B) 放在 `migrate-console-app-share` 子 change 内（按"分享发布"语义）

**决策**：选 (B)，列入本 change。理由：
1. UI 端"分享发布历史"页是分享流程闭环的最后一步（用户分享完想看历史发布版本列表），与 share record 高度耦合
2. `migrate-console-application-core` 已归档 38 endpoint，按路线图决策 2 "≤ 30 method 上限"严控边界，本方法不再回填
3. region URL `/v2/tenants/{namespace}/apps/{region_app_id}/releases` 与 `share_service` 的 region URL 同前缀，部署与回归测试更方便

**Controller 路径**：`AppReleasesController` 路径选 `/console/teams/{team_name}/groups/{group_id}/releases`，`{group_id}` 是 `service_group.group_id` int 主键；不选 `/teams/{team_name}/apps/{app_id}/releases` 是因为 `{app_id}` 已被 `migrate-console-application-core` 用于其他端点，避免歧义。

`region_app_id` 由 `serviceGroupRepo.findByGroupId(groupId).getRegionAppId()` 读取；当 group 未绑定 region_app_id（早期未走 region 同步的应用）时，按 rainbond Python 行为返回空 list（`200 + data.list=[]`），不抛异常。

## 决策 4 — Event 行的 region_share_id 字段复用

`ServiceShareRecordEvent` 与 `PluginShareRecordEvent` 两张表都有 `region_share_id` 列（32-char），用于关联 region 端的分享任务 ID。rainbond Python 端 `share_services.py:594 sync_event` 把 region 返回的 `bean.share_id` 写入此字段，后续 `get_sync_event_result` 用此字段拼 region 轮询 URL。

**决策**：本 change 严格复刻此字段语义：
- `ServiceShareController.addEvent` 在调 `shareService(...)` 后，把 region 返回的 `region_share_id` 回填到 `ServiceShareRecordEvent.regionShareId`
- `ServiceShareController.eventStatus`（GET 状态）从 event 行读 `region_share_id` 拼 region URL → `getShareServiceResult(...)` → 用返回的 `bean.status` 更新 `event_status`
- 对插件分享（`PluginShareRecordEvent`）做同样处理，对应 `getSharePluginResult`

事件级别的 `region_share_id` 缺失（rare race：事件刚创建但 region 未返）→ 状态查询返 200 + `event_status=pending`，不抛异常；后端 schedule（独立 hardening）可重试。

## 决策 5 — 错误透传与失败回滚

7 个 method 都遵守路线图决策 4 "共享规约"中的错误处理：

- region 异常（HTTP 4xx/5xx / 频率限制 / 集群授权）一律抛 `RegionApiException` 子类 → `GlobalExceptionHandler` 自动映射为 general_message 形状响应
- `msg_show` 优先用 region 自带的（Go 后端已汉化），缺失时由 `RegionErrorMsgEnricher` 兜底
- 不在 service 层 / controller 层硬编码中文 `msg_show`

**事务边界**：
- `addEvent` 在 `@Transactional` 内：本地 INSERT event → 调 `shareService(...)` → region 失败 → Spring 自动回滚事务（删除已 INSERT 的 event 行）；与 rainbond Python `share_services.py:577 sid = transaction.savepoint()` 行为一致
- `eventStatus`（GET）只读，无事务
- `complete` 校验全部 event=success → 翻 status=1；任一 failure → 抛 `ServiceHandleException(409, "share not all success", "存在失败事件，请放弃后重试")`；不调 region

**特殊错误码**：
- region 端返 `409`（频繁操作）→ rainbond Python `share_services.py:614 raise ServiceHandleException(...409, "操作过于频繁，请稍后再试")`；本 change 由 `RegionApiFrequentException` 自动映射，msg_show 同步透传
- region 端返 `403`（云市授权）→ rainbond Python `share_services.py:1213 raise ServiceHandleException(403, "云市授权不通过", error_code=10407)`；本 change 仅在 `shareCloudService` 路径出现，由 `RegionApiException(403,...)` 透传，msg_show 由 region 给

## 决策 6 — 测试用 region 真实数据 vs Mock

`ShareOperationsImplTest` 走 `MockRestServiceServer`（与已有 `ClusterOperationsImplTest` / `HelmOperationsImplTest` 同模式），覆盖：
- 7 method 各 1 happy path（断言 URL / HTTP method / namespace 替换 / body JSON 形状）
- 5xx 透传（断言 `RegionApiException.httpStatus`）
- namespace fallback（`Tenants.namespace == null` → 回退 `tenant_name`）
- 频率限制（429 → `RegionApiFrequentException`）
- region_share_id 缺失场景（getShareServiceResult 返 `bean.status=null` 时不刷本地）

集成测试（`ShareLifecycleIntegrationTest`）走 `@SpringBootTest + @ActiveProfiles({"local","contract-test"}) + @MockitoBean ShareOperations`，断言：
- 6-step happy path（POST events → mock 返 region_share_id → GET status mock 返 success → POST complete）
- 失败回滚（region 抛 503 → 事务 rollback，event 表无新行）
- plugin 分享场景（PluginShareController.addEvent → mock sharePlugin → 断言 PluginShareRecordEvent 持久化）
- cloud share 场景（直接调用 `shareOps.shareCloudService` 接口路径，验证 controller 层未触达）
- region 5xx 透传（断言响应 `code` 与 `msg_show` 来自 region）
- publish status happy（`/apps/{alias}/publish/status?service_key=&app_version=` → 200）
- releases 列表 happy（`/groups/{group_id}/releases` 取 region_app_id → 200 + `data.list`）

不依赖本地起 region 容器（项目既定测试规约）。

## 非决策（明确不做）

- **不**修改 `ServiceShareRecord` / `ServiceShareRecordEvent` / `TenantPluginShare` / `PluginShareRecordEvent` 4 张表 schema（rainbond Django migrations 拥有，本 change 严格复用既有列）
- **不**新建 ServiceShareController / PluginShareController（既有类内修改即可，避免路径重定义）
- **不**在 `ShareOperations` 接口中合并云市与本地市场调用（保持 7 个 method 1:1 对齐 rainbond 锚点）
- **不**实现 app_template 序列化 / 反序列化（属 import-export）
- **不**实现 `RainbondCenterApp` publish 完成标记（属 marketplace 子 change，跨表事务复杂）
- **不**实现 share_service 之外的 cloud share controller endpoint（`shareCloudService` 仅在接口层暴露给后续 marketplace OAuth 子 change 调用）
- **不**实现"share record giveup 时反向通知 region"（rainbond 行为同 —— region 端任务是 fire-and-forget，giveup 仅本地翻 status=2）

## 测试约定

集成测试覆盖（`ShareLifecycleIntegrationTest`）：

- `service_share_full_lifecycle_happy`：6-step 全程 mock region，断言每步 `service_share_record.step` 与 `service_share_record_event.event_status` 推进
- `service_share_event_addition_rollback_on_region_5xx`：mock `shareService` 抛 `RegionApiException(503,...)`，断言事务回滚 + 事件表无新行 + 响应 503 + msg_show 来自 region
- `plugin_share_event_persists_region_share_id`：plugin 分享路径，mock `sharePlugin` 返 `bean.share_id="psid-123"`，断言 `PluginShareRecordEvent.regionShareId="psid-123"`
- `cloud_share_interface_dispatchable_without_controller`：直接 service 层注入 `ShareOperations.shareCloudService(...)` mock，断言接口可调；不通过 controller
- `share_service_result_polling_status_progression`：先 addEvent → mock 返 region_share_id → GET event status mock 返 `success` → 断言本地 `event_status` 更新为 success
- `publish_status_query_happy`：`/apps/{alias}/publish/status?service_key=foo&app_version=1.0` → mock 返 `bean.status="published"` → 200 + `data.bean.status="published"`
- `app_releases_list_resolves_region_app_id`：`/groups/{group_id}/releases` → 通过 `serviceGroupRepo` 取 `regionAppId` → mock `listAppReleases` 返 list → 200 + `data.list`
- `complete_rejected_when_event_failed`：构造 1 个 event_status=failure，POST complete → 409 + `msg_show=存在失败事件，请放弃后重试`

每个 controller endpoint 至少 1 happy + 1 region 异常透传；契约形状 5 项断言（`code/msg/msg_show/data.bean/data.list`）。

## 实施期探测结果（2026-05-10 落地）

- **既有 controller 注入扩展**：`ServiceShareController` 已存在 13 endpoint（含 `addEvent` / `pluginEvent` / `complete`），本 change 在原 controller 类内追加 `eventStatus` GET endpoint + 改造 `addEvent` / `pluginEvent` 接 region；`PluginShareController` 同模式（追加 `eventStatus`）。stub 状态下 `addEvent` 把 `event_id` 直写为 `region_share_id` —— 改造后改写为 region 返回的 `bean.share_id`，并把 `event_status` 从 `running` 推进为 `start`
- **新增 finder**：`ServiceShareRecordEventRepository` / `PluginShareRecordEventRepository` 各加 `findByRecordIdAndEventId(recordId, eventId)`（用于 status 轮询定位 event 行）；`RegionAppRepository` 加 `findFirstByAppId(appId)`（AppReleasesController 通过 `service_group.id (= app_id)` 反查 `region_app_id`）
- **AppReleasesController group_id 解析**：design.md 决策 3 提到 `service_group.group_id` 列，实际 entity PK 是 `Integer ID`（无独立 group_id 列）；controller 用 `serviceGroupRepo.findById(Integer.parseInt(group_id))` 解析；region_app_id 通过 `region_app` 表（`RegionApp` entity）按 `app_id` 反查
- **listAppReleases 响应 list 提取**：rainbond Python `regionapi.py:2389 list_app_releases` 直接返回 `body["list"]`，本 change 在 `ShareOperationsImpl` 用 `processor.checkStatus(...)` 拿 root JsonNode 后递归找 `list` / `data.list` 节点，returns `List<Object>`；controller 用 `GeneralMessage.okList(...)` 封装
- **RegionApiFrequentException code=429**：测试发现 `GlobalExceptionHandler` 把 frequent 异常映射为 HTTP 409 + body code=429（rainbond 历史保留：HTTP status 与业务 code 不对齐），集成测试断言 `$.code = 429`
- **shareCloudService 接口保留但 controller 不暴露**：按决策 2 落地，UI v3.5+ 入口已移除，留给后续 marketplace OAuth 子 change 复用
- **测试结果**：`ShareOperationsImplTest` 9 单测 + `ShareLifecycleIntegrationTest` 9 集成 = 18 用例全过；既有 controller 13 endpoint 不破坏
