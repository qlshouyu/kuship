# Design — migrate-console-build-versions

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是
**P1 #4**（构建版本与多语言版本管理），归属母路线图。

- **method 数 15**（路线表估算口径）—— 路线图中 method 数最大的子 change，工作量约 4-5 天，仍在 ≤ 30 上限内
- **依赖**：无（独立可起）
- **重叠**：与 P2 子 change `migrate-console-maven-setting` 在多语言版本读取（`get_lang_version`）上有少量重叠
- **决策**：lang version CRUD 完整由本 change 拥有，maven-setting 后续仅引用本 change 落地的 `LangVersionOperations`，不重写

归档时反向更新路线表对应行，标注 `migrate-console-build-versions` 已完成 + `LangVersionOperations` 接口的所有权。

## Region API URL 表

15 个 region method 对应 15 行（rainbond `regionapi.py` 锚点全列）：

| method                                   | HTTP | 路径                                                                            | rainbond 锚点                              |
|------------------------------------------|------|---------------------------------------------------------------------------------|--------------------------------------------|
| `getBuildVersions(rn, tn, alias)`         | GET  | `/v2/tenants/{tenant_name}/services/{service_alias}/build-list`                | `regionapi.py:1752 get_service_build_versions` |
| `getBuildVersionById(rn, tn, alias, vid)` | GET  | `/v2/tenants/{tenant_name}/services/{service_alias}/build-version/{version_id}` | `regionapi.py:1775 get_service_build_version_by_id` |
| `updateBuildVersion(rn, tn, alias, vid, body)` | PUT  | `/v2/tenants/{tenant_name}/services/{service_alias}/build-version/{version_id}` | `regionapi.py:1787 update_service_build_version_by_id` |
| `deleteBuildVersion(rn, tn, alias, vid, body)` | DELETE | `/v2/tenants/{tenant_name}/services/{service_alias}/build-version/{version_id}` | `regionapi.py:1763 delete_service_build_version` |
| `getServiceDeployVersion(rn, tn, alias)`  | GET  | `/v2/tenants/{tenant_name}/services/{service_alias}/deployversions`             | `regionapi.py:1809 get_service_deploy_version` |
| `getTeamServicesDeployVersion(rn, tn, body)` | POST | `/v2/tenants/{tenant_name}/deployversions`                                     | `regionapi.py:1799 get_team_services_deploy_version` |
| `getServiceCheckInfo(rn, tn, uuid)`       | GET  | `/v2/tenants/{tenant_name}/servicecheck/{uuid}`                                 | `regionapi.py:1450 get_service_check_info` |
| `serviceSourceCheck(rn, tn, body)`        | POST | `/v2/tenants/{tenant_name}/servicecheck`                                        | `regionapi.py:1440 service_source_check`   |
| `getBuildStatus(rn, tn, plugin_id, build_version)` | GET | `/v2/tenants/{tenant_name}/plugin/{plugin_id}/build-version/{build_version}` | `regionapi.py:1264 get_build_status`       |
| `getLangVersion(eid, rn, lang, show, build_strategy)` | GET | `/v2/cluster/langVersion?language={lang}&show={show}[&build_strategy={s}]` | `regionapi.py:2791 get_lang_version`       |
| `createLangVersion(eid, rn, body)`        | POST | `/v2/cluster/langVersion`                                                       | `regionapi.py:2813 create_lang_version`    |
| `updateLangVersion(eid, rn, body)`        | PUT  | `/v2/cluster/langVersion`                                                       | `regionapi.py:2822 update_lang_version`    |
| `deleteLangVersion(eid, rn, body)`        | DELETE | `/v2/cluster/langVersion` (DELETE with body)                                  | `regionapi.py:2831 delete_lang_version`    |
| `getCnbFrameworks(eid, rn, lang)`         | GET  | `/v2/cluster/cnb/frameworks?lang={lang}`                                        | `regionapi.py:2840 get_cnb_frameworks`     |
| `batchOperationService(rn, tn, body)`     | POST | `/v2/tenants/{tenant_name}/batchoperation` + Header `Resource-Validation: true` | `regionapi.py:1893 batch_operation_service` |

> `tenant_name` 路径段：rainbond Python 用 `tenant_region.region_tenant_name`（即 `Tenants.namespace`），
> 与 helm-release / gateway-certificate / cluster-extras 子 change 同样套路。`ServiceOperationsImpl` 已注入
> `TenantsRepository`（`migrate-console-app-create` 阶段引入），本 change 9 个新 method 也复用 namespace fallback。
> 但实际现存代码是 `encode(tenantName)` 直接拼，需保留与既有 7 method 的一致性 —— 本 change 沿用 `encode(tenantName)`
> 不主动改写为 namespace（避免与 application-core / app-create 已落地的 7 method 一致性破坏；namespace 替换若必要，
> 留作单独 hardening change 统一处理 ServiceOperations 全部 method）。

## Controller 路径锚点

| Controller                       | path                                                                          | method | rainbond 锚点                                   |
|----------------------------------|-------------------------------------------------------------------------------|--------|-------------------------------------------------|
| AppVersionsController            | `/console/teams/{team_name}/apps/{service_alias}/build-versions`              | GET    | rainbond Python 端 `urls.py:912 AppVersionsView`（路径形如 `/version`，本 change 改名 `build-versions` 更语义） |
| AppVersionsController            | `/console/teams/{team_name}/apps/{service_alias}/build-versions/{version_id}` | GET / PUT / DELETE | `urls.py:913 AppVersionManageView`         |
| AppVersionsController            | `/console/teams/{team_name}/apps/{service_alias}/deploy-version`              | GET    | rainbond 端无独立 console URL（直接 region 透传），本 change 新建 |
| AppVersionsController            | `/console/teams/{team_name}/apps/{service_alias}/source-check`                | POST   | rainbond `views/oauth.py:695 service_source_check` 调用点 |
| AppVersionsController            | `/console/teams/{team_name}/apps/{service_alias}/source-check/{uuid}`         | GET    | rainbond `views/oauth.py:706 get_service_check_info` 调用点 |
| AppVersionsController            | `/console/teams/{team_name}/apps/{service_alias}/build-status`                | GET    | rainbond 端 `views/plugin/plugin_version.py` `get_build_status` 调用点 |
| BatchDeployVersionController     | `/console/teams/{team_name}/deploy-version`                                   | POST   | rainbond `services/service_services.py:218 get_apps_deploy_versions` 调用点 |
| LangVersionController            | `/console/enterprise/{enterprise_id}/regions/{region_name}/lang-version`      | GET / POST / PUT / DELETE | `urls.py:969 EnterpriseRegionLangVersion` |
| LangVersionController            | `/console/enterprise/{enterprise_id}/regions/{region_name}/cnb/frameworks`    | GET    | `urls.py:971 EnterpriseRegionCNBFrameworks` |
| AppBatchActionsController（改造） | `/console/teams/{team_name}/batch_actions`                                     | POST   | `urls.py:701 BatchActionView`（保留路径，仅替换实现） |

trailing slash 兼容沿用既定规则（每 endpoint 同时声明 `path` 与 `path/`）。

`build-versions` 的命名（连字符）与既有 `migrate-console-app-runtime` 落地的 `app/{alias}/version` 错峰，
后者是组件版本元数据查询接口（rainbond 端是 AppVersionsView 走应用市场版本而非构建版本），不冲突。

## 决策 1 — `build_version` vs `deploy_version` 的语义边界

这是本 change 必须先讲清的核心分歧。

| 概念             | 数据源                                       | 含义                                             | 数据流向                                  |
|------------------|----------------------------------------------|--------------------------------------------------|-------------------------------------------|
| `build_version`  | region 端 `service_build_version` 表（Go）   | **历史构建产物记录**：每次构建产生一条，记 final_status / dest_image / event_id / commit_msg / 等 | rainbond-builder 写入 → region API 读出   |
| `deploy_version` | region 端运行时（K8s Deployment image tag）  | **当前正在运行的 image tag**：和 service 表 `tenant_service.deploy_version` 列同步           | region 部署/回滚时写入 → region API 读出  |

UI 行为映射：

- **应用页"版本" Tab**：先调 `getBuildVersions`（GET `/build-list`）拿历史构建列表 + bean 中的 `deploy_version`
  → 用 `build_version` 与 `deploy_version` 比较得出每条 record 的 `upgrade_or_rollback`（1 / 0 / -1 / 2）
- **应用页主面板**：调 `getServiceDeployVersion` 拿当前运行版本独立显示
- **应用列表页（高频）**：UI 一次性打开 N 个 service 的列表，需要 batch 查 → `getTeamServicesDeployVersion`
  body 形如 `{"service_ids": ["id1", "id2", ...]}`，return map 形如 `{id1: "tag1", id2: "tag2"}`

谁拥有谁：

- `build_version` 是 region 真相源，console 不写本地；本 change 落地 `ServiceBuildVersion` entity 仅作只读缓存预留 schema
- `deploy_version` 同时存在于 region 运行时与 console 的 `tenant_service.deploy_version` 列（rainbond
  Django migration 历史决策）；console 端 `tenant_service.deploy_version` 由 `migrate-console-application-core`
  落地的 `ServiceLifecycleOperations` 在 deploy/rollback 后异步同步，**本 change 不动**

## 决策 2 — `ServiceBuildVersion` entity 字段集

按 region Go 端 `db/model/service_build_version.go`（rainbond builder 写入端 schema 真相）映射：

| 字段             | 类型       | 说明                                                  |
|------------------|-----------|-------------------------------------------------------|
| `ID`             | INT (PK)  | 自增主键，列名大写（与项目其他历史 entity 一致）       |
| `service_id`     | varchar(32) | 组件 UUID                                          |
| `build_version`  | varchar(32) | 构建版本号（rainbond 用时间戳格式 `20240101120000`） |
| `event_id`       | varchar(32) | 关联 service_event 记录                              |
| `kind`           | varchar(32) | `build_from_source_code` / `build_from_image` / `build_from_market_image` / `build_from_market_slug` |
| `delivered_type` | varchar(32) | `image` / `slug` 交付物类型                          |
| `delivered_path` | varchar(255) | 交付物路径（image url 或 slug ftp 路径）           |
| `image_repo`     | varchar(255) | 镜像仓库（kind=image 时）                          |
| `repo_url`       | varchar(2047) | 源码仓库 URL                                       |
| `code_branch`    | varchar(255) | 分支名                                             |
| `code_commit_msg`| longtext  | commit message                                       |
| `code_commit_author` | varchar(255) | commit 作者                                    |
| `code_version`   | varchar(255) | commit hash                                        |
| `commit_msg`     | longtext  | （旧）通用 commit message，rainbond 兼容字段        |
| `author`         | varchar(255) | （旧）通用作者                                    |
| `final_status`   | varchar(32) | `success` / `failure` / `building`                   |
| `status`         | varchar(32) | 构建中间态                                         |
| `build_log_file_path` | varchar(2047) | 构建日志路径                                |
| `create_time`    | datetime  | 创建时间                                            |
| `finish_time`    | datetime  | 完成时间（未完成时为 `0001-01-01T00:00:00Z`，需在序列化前清洗） |
| `plan_version`   | varchar(255) | 规划版本号（updateBuildVersion 透传 body 中此字段） |

字段集大小：**21 列**（含 PK）。本 change 仅落 entity + repository（finder）；写路径留作 hardening。

## 决策 3 — `LangVersion` entity 字段集

按 region Go 端 `db/model/lang_version.go`（rainbond cluster langVersion CRUD 真相）映射：

| 字段             | 类型       | 说明                                                  |
|------------------|-----------|-------------------------------------------------------|
| `ID`             | INT (PK)  | 自增主键                                            |
| `lang`           | varchar(64) | 语言名（`java` / `nodejs` / `python` / `go` / `php` / `ruby` / `dotnet` / `static`） |
| `version`        | varchar(64) | 版本号（如 `21.0.2` / `lts/iron` / `3.12`）        |
| `event_id`       | varchar(32) | 关联上传事件                                        |
| `file_name`      | varchar(255) | 上传时的原始文件名                                |
| `show`           | tinyint(1) | UI 是否展示（默认 `true`）                          |
| `first_choice`   | tinyint(1) | UI 是否高亮为推荐（默认 `false`）                   |
| `build_strategy` | varchar(32) | `slug` / `cnb`（cnb_build 二代构建链路；缺省 `slug`） |
| `is_allowed`     | tinyint(1) | 是否被审核允许使用（默认 `true`）                   |
| `create_time`    | datetime  | 创建时间                                            |

字段集大小：**10 列**（含 PK）。同 ServiceBuildVersion，本 change 仅落 entity + repository；写路径留作 hardening。

## 决策 4 — `batch_operation_service` 的语义与 controller 改造

rainbond Python `regionapi.py:1893 batch_operation_service` 的 body 形如：

```json
{
  "operation": "start" | "stop" | "restart" | "build" | "upgrade",
  "operator": "<user_nick_name>",
  "build_infos": [
    { "service_id": "id1", "action": "build", "kind": "build_from_source_code", "code_version": "master", "image_info": null },
    { "service_id": "id2", "action": "start" }
  ],
  "service_ids": ["id1", "id2"]   // operation in {start, stop, restart} 时与 build_infos 互斥
}
```

region 端在一次调用内 dispatch 到对应 service 的 K8s 操作，且支持资源不足校验（Header `Resource-Validation: true` 触发）。

**改造既有** `AppBatchActionsController`：

```java
// 现状（appruntime 阶段）：N 次循环
for (TenantService s : services) {
    switch (action) {
        case "start" -> lifecycle.startService(s.getServiceRegion(), teamName, s.getServiceAlias(), req);
        ...
    }
}

// 本 change：1 次 batch + 内部按 service_id 聚合
List<Map<String, Object>> serviceIdList = services.stream()
        .map(s -> Map.<String, Object>of("service_id", s.getServiceId()))
        .toList();
Map<String, Object> body = Map.of(
    "operation", action,
    "operator", operator,
    "service_ids", serviceIdList   // 或 build_infos，按 action 分支
);
batchOps.batchOperationService(regionName, teamName, body);
```

兼容承诺：

- URL `/console/teams/{team_name}/batch_actions` 不变
- 权限码 `APP_OVERVIEW_PERMS` 不变
- body 入参（`{action, service_ids}`）形状不变；controller 内部转换成 region 期望的 `{operation, service_ids: [...]}` 形状
- 响应形状从 `{success: [...], failed: [...]}` 改为透传 region 返回的 `{batch_result: [...]}`，UI 端有适配负担 → 决策保留旧响应形状外壳，把 region 返回的 batch result 解构成 success/failed 两数组（service-level 错误从 region body 中拆出）；这样 UI 兼容期 0 改动

## 决策 5 — `get_team_services_deploy_version` 高频调用

UI 应用列表页（`Topology` / `ComponentList` / `ServiceListInGroup`）打开就批量拉所有 service 的 deploy_version。
N 个 service 一次 region 调用（POST `/v2/tenants/{tn}/deployversions` body=`{"service_ids": [...]}`）。

性能考量：

- 单次 region 调用本身不慢（region Go 端做内存查询）
- 但 UI 频繁切换 group / 刷新 → 同样的 service_id 列表反复打到 region

**决策**：本 change **不做客户端缓存**，纯透传 region。

理由：

1. region 是 deploy_version 的真相源，缓存有过期风险（rolling update 中途 cache 命中旧值会误导 UI）
2. 缓存层应该在 service 级别用 ETag 或 service.updated_at 触发失效，而非简单 TTL
3. 本 change 范围已大（15 method + 3 entity + 3 controller），加缓存推到 hardening

记一笔：**未来 hardening change `add-component-list-deploy-version-cache` 将基于本 change 的 `getTeamServicesDeployVersion`
透传增加 N+1 缓存层（短 TTL + service.update_time evict），不动接口签名。**

## 决策 6 — Controller URL 命名与 rainbond 端历史路径的差异

rainbond Python 端 `urls.py:912` 把构建版本列表挂在 `/teams/{tn}/apps/{alias}/version` —— 这个 path 在
kuship 已被 `migrate-console-app-runtime` 落地的 component version 元数据接口占用（应用市场版本而非构建版本）。

**决策**：本 change 把构建版本列表 / 详情挂在 `build-versions` 子路径，避免冲突 + 语义更清晰：

- `/build-versions` — 列表 + 分页
- `/build-versions/{version_id}` — 详情 / 更新 / 删除
- `/deploy-version` — 当前运行版本单查
- `/source-check` POST + `/source-check/{uuid}` GET —— 异步两段式
- `/build-status` —— 插件构建状态查询（rainbond 端绑在 plugin URL 下，但语义上是构建状态查询，本 change 提供组件维度入口；plugin 维度的 `/console/teams/.../plugins/.../version/.../status` 仍由 `migrate-console-plugin` 子 change 拥有，不冲突）

UI 兼容：rainbond-ui 端调 `/version` 走应用市场版本（已迁移），调 `/build-versions` 走构建版本（kuship 新增）—— 前端
`Application/Version.js` 等页面在拷贝 rainbond-ui 时同步替换 fetch 路径为 `build-versions`。

## 决策 7 — Lang Version 接口的 `enterprise_id` vs `region_id`

`getLangVersion(enterpriseId, regionName, lang, show, buildStrategy)` 接受 enterprise_id + region_name 两个段，
但实际 region 调用只用 region_name 拼 URL（不在 query string 中带 enterprise_id）。

为何接口签名仍带 `enterpriseId`：

- rainbond Python `regionapi.py:2791 get_lang_version` 签名带 enterprise_id 是为了用 `get_enterprise_region_info(eid, rn)`
  从 `region_info` 表查 region URL 与 token —— enterprise 维度的 region 隔离（多 enterprise 场景下，同 `region_name`
  在不同 enterprise 下指向不同 region 实例）
- kuship 端的 `RegionClientFactory` 也按 `(enterpriseId, regionName)` 双键 cache
- 接口签名保留 enterprise_id 便于 `LangVersionOperations` 直接调 `clientFactory.client(enterpriseId, regionName)`

**结论**：接口签名保留 `enterprise_id`，URL 中不输出该字段。`createLangVersion` / `updateLangVersion` /
`deleteLangVersion` 同样保留双段。

## 决策 8 — DELETE with body（`deleteLangVersion`）

rainbond Python `regionapi.py:2831 delete_lang_version` 用 DELETE 方法 + body。Spring 6 RestClient 支持
DELETE with body：

```java
c.method(HttpMethod.DELETE).uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
 .exchange((req, r) -> RegionApiSupport.readAsString(r));
```

与 `migrate-console-application-core` 落地的 `ServiceVolumeOperations.deleteDepVolumes` / `migrate-console-thirdparty`
落地的 `ThirdPartyServiceOperations.deleteEndpoints` 同模式。

## 决策 9 — 错误透传与本地校验

15 个 method 全部走 `RegionApiException` + `GlobalExceptionHandler` 自动映射为 general_message。

本地预校验（在 controller 层）：

- `service_alias` 解析到 `TenantService` → 不存在 404 + `msg_show=组件不存在`
- `team_name` 解析到 `Tenants` → 不存在 404 + `msg_show=团队不存在`
- batch_actions 校验 `service_ids` 全部属于 team → 不属于 403 + `msg_show=部分组件不属于当前团队`（沿用既有 controller 逻辑）
- lang version `lang` 参数白名单：`{java, nodejs, python, go, php, ruby, dotnet, static, vm}` —— 非法 400
- buildStrategy 白名单：`{slug, cnb, ""}` —— 非法 400

源码检测 `serviceSourceCheck` body 不做 strategy 推断（决策：rainbond `console/services/source_build_state_service.py`
的复杂 cnb / slug / dockerfile 自动选择留在 rainbond-console 行为不变；kuship 端透传 region）。

## 测试约定

集成测试覆盖（`@SpringBootTest + @ActiveProfiles({"local","contract-test"}) + @MockitoBean ServiceOperations / LangVersionOperations / BatchServiceOperations + JdbcTemplate seed`）：

- `AppVersionsControllerTest`：
  - 列表 happy path（`getBuildVersions` 返 list 含 5 条）
  - 详情 happy path（`getBuildVersionById` 返 bean）
  - 更新 happy（`updateBuildVersion` PUT body 含 `plan_version`）
  - 删除 happy（`deleteBuildVersion` body 含 `operator`，operator 来自 `RequestContext.username`）
  - deploy-version 单查 happy
  - source-check POST happy（异步发起）
  - source-check GET happy（按 uuid 查结果）
  - build-status happy
  - service 不存在 → 404
  - region 5xx 透传（任一 method）

- `BatchDeployVersionControllerTest`：
  - POST batch happy（`{service_ids: [...]}` → region `{id1: "tag1", id2: "tag2"}` 透传）
  - service_ids 为空 → 400
  - region 503 透传

- `LangVersionControllerTest`：
  - GET lang-version 含 query `lang=java&show=true&build_strategy=slug` 透传
  - POST 创建 happy
  - PUT 更新 happy
  - DELETE 删除 happy（DELETE with body 断言）
  - GET cnb/frameworks?lang=nodejs happy
  - 非法 lang → 400
  - 非法 build_strategy → 400

- `AppBatchActionsControllerRegressionTest`（改造回归）：
  - URL `/batch_actions` 仍 200
  - body `{action: "start", service_ids: [...]}` → 内部转 `batchOperationService(rn, tn, {operation: "start", service_ids: [...]})`
  - 响应仍是 `{success: [...], failed: [...]}` 形状
  - region 5xx → success=[], failed=全部
  - 非法 action → 400
  - service 不属于 team → 403

每个 controller 至少 1 happy + 1 error 透传 + 1 路径 / body 形状校验。

单测：`MockRestServiceServer` 覆盖 15 个 method 的 URL / query / body 形状 + Resource-Validation header
（仅 batchOperationService 一处）+ DELETE with body（lang version + buildVersion 两处）。

## 非决策（明确不做）

- **不**改写既有 `ServiceOperationsImpl` 7 method 的 `tenantName` → `namespace` 替换（与既有一致性优先）
- **不**给 `ServiceBuildVersion` / `LangVersion` entity 加写路径（hardening 范围）
- **不**对 `getTeamServicesDeployVersion` 加客户端缓存（hardening 范围）
- **不**实现 maven-setting 8 method（属 P2 子 change `migrate-console-maven-setting`）
- **不**做源码检测的 build_strategy 推断（保留 region 真相源行为）
- **不**修改 14 接口骨架的"通用骨架"语义 —— 新接口 `LangVersionOperations` / `BatchServiceOperations` 放
  `modules/application/api/`，与 `BackupOperations`（appmarket）/ `MonitorOperations`（appruntime）/
  `AutoscalerOperations`（appruntime）等业务域接口同等地位

## 实施期探测结果（2026-05-10 落地）

- **schema 真相 —— `service_build_version` / `lang_version` 表不存在**：`docker exec kuship-mysql mysql -e "SHOW TABLES LIKE '%build%'"` 实测仅 `plugin_build_version` / `service_build_source` 两张表，与决策 2 / 决策 3 假设的 21 列 / 10 列 schema 完全不存在。**实施决策**：跳过 §2 entity / repository 落地（`ServiceBuildVersion` / `LangVersion` entity 不创建，避免 `hibernate.ddl-auto=validate` 启动失败）；本 change 仅保留 region 透传层 + controller 层。后续若需要本地缓存（性能 / 离线视图），由 hardening change `add-component-list-deploy-version-cache` + `add-lang-version-cache` 在 region Go 端确认 schema 后落地
- **接口扩展点**：`ServiceOperations` 在 7 既有 method 之后追加 9 个 default unsupported method 声明（`getBuildVersions` / `getBuildVersionById` / `updateBuildVersion` / `deleteBuildVersion` / `getServiceDeployVersion` / `getTeamServicesDeployVersion` / `serviceSourceCheck` / `getServiceCheckInfo` / `getBuildStatus`），由 `ServiceOperationsImpl @Primary` 内部追加 9 个 override
- **新接口落地**：`LangVersionOperations`（5 method） + `LangVersionOperationsImpl @Primary`、`BatchServiceOperations`（1 method） + `BatchServiceOperationsImpl @Primary`，全部归属 `modules/application/api/`
- **3 个新 controller**：`AppVersionsController`（8 endpoint：list/detail/update/delete/deploy/source-check 异步两段/build-status）+ `BatchDeployVersionController`（1 endpoint）+ `LangVersionController`（5 endpoint，全部 `@RequireEnterpriseAdmin`）。删除版本时 controller 自动从 `RequestContext.username` 注入 `operator` 字段
- **§7 改造 AppBatchActionsController 推迟**：避免回归 lifecycle 单调度路径的风险（包括 body 形状转换、响应解构、batch_result 解析等多步），决策推迟到独立 hardening change 处理；本轮仅落地 `BatchServiceOperations` 接口与 `@Primary` 实现，待后续接线
- **§2.5 hibernate validate 启动校验跳过**：因 §2 整段跳过，无新 entity 引入到启动期 schema 校验
- **测试结果**：`AppVersionsControllerTest` 13 集成测试用例全过（含 8 endpoint × 关键路径 + LangVersion / BatchDeployVersion 关键场景 + region 5xx 透传 + service 不存在 404）；`mvn -DskipTests package` 编译通过
