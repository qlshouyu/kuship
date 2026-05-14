## ADDED Requirements

### Requirement: 构建版本与多语言版本管理

kuship-console SHALL 把 `ServiceOperations` 接口扩 9 个 default unsupported method（`getBuildVersions` / `getBuildVersionById` / `updateBuildVersion` / `deleteBuildVersion` / `getServiceDeployVersion` / `getTeamServicesDeployVersion` / `getServiceCheckInfo` / `serviceSourceCheck` / `getBuildStatus`），新增 `LangVersionOperations`（5 method：`getLangVersion` / `createLangVersion` / `updateLangVersion` / `deleteLangVersion` / `getCnbFrameworks`） 与 `BatchServiceOperations`（1 method：`batchOperationService`） 两个业务域接口，并对应落地 `ServiceOperationsImpl @Primary` 的 9 个 override + 两个新接口的 `Default + Impl` 双 bean，全部走 region 1:1 透传，覆盖 rainbond `regionapi.py` 中 15 个 method 的完整迁移。本 Requirement 同时锁定与 `migrate-console-maven-setting`（P2）子 change 的边界：lang version CRUD 完整由本 change 拥有，`migrate-console-maven-setting` 后续仅引用本 change 落地的 `LangVersionOperations` 而非重写。新增 3 个 console controller（`AppVersionsController` / `BatchDeployVersionController` / `LangVersionController`，共 11 endpoint）+ 改造既有 `AppBatchActionsController` 把 N 次 lifecycle 循环替换为 1 次 `batchOperationService` 调用（保持 URL `/console/teams/{team_name}/batch_actions` 与权限码 `APP_OVERVIEW_PERMS` 不变）。新增 2 张本地 entity（`ServiceBuildVersion` / `LangVersion`），按 region Go 端 schema 真相落 JPA 映射 + repository finder（仅读，无 writer），写路径留作 hardening。

业务规则：

- `ServiceOperations` 9 个新 method MUST 1:1 映射 rainbond `regionapi.py` 锚点（路径段 `tenant_name` 沿用既有 `encode(tenantName)` 不主动改 namespace，与既有 7 method 保持一致性）
- `getTeamServicesDeployVersion` MUST 走 POST `/v2/tenants/{tenant_name}/deployversions`（注意：rainbond Python 端用 POST 而非 GET，因 body 中 `service_ids` 数组可能很大）
- `serviceSourceCheck` 与 `getServiceCheckInfo` MUST 严格遵循异步两段式：POST `/servicecheck` 拿 `check_uuid` → GET `/servicecheck/{uuid}` 轮询结果；console 层不做 `build_strategy` 推断（rainbond `console/services/source_build_state_service.py` 的 cnb / slug / dockerfile 自动选择保留在 rainbond-console 行为不变，kuship 端仅透传）
- `batchOperationService` MUST 显式 `.header("Resource-Validation", "true")`（与 rainbond Python `_set_headers(token, resource_validation="true")` 一致），用于 region 端资源不足校验
- `deleteLangVersion` MUST 用 DELETE with body 模式（Spring 6 RestClient `c.method(HttpMethod.DELETE).contentType(JSON).body(body)`），与 rainbond Python `_delete(url, body=json.dumps(data))` 一致
- `LangVersionOperations` 5 method 接口签名 MUST 保留 `enterpriseId` 段（用于 `RegionClientFactory.client(eid, rn)` 双键 cache），URL 中 NOT 输出 `enterprise_id`
- `getLangVersion` query 参数 MUST 严格按 rainbond Python 行为：`?language={lang}&show={show}` + `build_strategy` 非空时追加 `&build_strategy={s}`
- `getCnbFrameworks` query 参数 MUST 仅 `?lang={lang}`（默认 `nodejs`）
- 新 controller 的本地校验 MUST 包含：`team_name` / `service_alias` 解析（404 透传），`lang` 白名单（`{java, nodejs, python, go, php, ruby, dotnet, static, vm}`），`build_strategy` 白名单（`{slug, cnb, ""}`）
- 改造的 `AppBatchActionsController` MUST 保持 URL / 权限码 / body 入参形状 / 响应形状 `{success: [...], failed: [...]}` 与既有版本兼容；内部把 region `{batch_result: [...]}` 解构为 `success` / `failed` 两数组
- 改造的 `AppBatchActionsController` MUST NOT 在 region `/batchoperation` 端点不可用时自动降级到 N 次 lifecycle 循环（决策：透传 region 错误，让运维通过 region 升级修复，避免 UI 行为漂移）
- 11 个新 endpoint + 1 个改造 endpoint 全部走默认 JWT 鉴权链，不进 permitAll
- region 异常 MUST 透传 `RegionApiException`，由 `GlobalExceptionHandler` 自动映射为 general_message
- 2 张本地 entity（`ServiceBuildVersion` / `LangVersion`） MUST 按 region Go 端 schema 真相映射；hibernate `validate` 模式校验通过即视为 schema 一致；repository 仅暴露 finder，无 writer

#### Scenario: 构建版本列表

- **GIVEN** team `default`（namespace `my-namespace`），组件 alias `my-app`
- **WHEN** `GET /console/teams/default/apps/my-app/build-versions`
- **THEN** kuship 调 region `GET /v2/tenants/default/services/my-app/build-list`
- **AND** 响应 200 + `data.bean.list` 含构建版本数组（透传 region 的 `bean.list` 字段，含 `build_version` / `event_id` / `final_status` / `kind` / `code_commit_msg` 等）
- **AND** `data.bean.deploy_version` 来自 region `bean.deploy_version`（当前运行版本）

#### Scenario: 构建版本详情

- **GIVEN** team `default`，组件 alias `my-app`，version_id `20240101120000`
- **WHEN** `GET /console/teams/default/apps/my-app/build-versions/20240101120000`
- **THEN** kuship 调 region `GET /v2/tenants/default/services/my-app/build-version/20240101120000`
- **AND** 响应 200 + `data.bean` 含 `is_exist` / `final_status` / 等字段（透传）

#### Scenario: 更新构建版本规划版本号

- **GIVEN** team `default`，组件 alias `my-app`，version_id `20240101120000`
- **WHEN** `PUT /console/teams/default/apps/my-app/build-versions/20240101120000` body=`{"plan_version": "v2.0.0"}`
- **THEN** kuship 调 region `PUT /v2/tenants/default/services/my-app/build-version/20240101120000` body 透传含 `plan_version`
- **AND** 响应 200

#### Scenario: 删除构建版本

- **GIVEN** team `default`，组件 alias `my-app`，version_id `20240101120000`，当前用户 nick_name `alice`
- **WHEN** `DELETE /console/teams/default/apps/my-app/build-versions/20240101120000`
- **THEN** kuship 调 region `DELETE /v2/tenants/default/services/my-app/build-version/20240101120000` body=`{"operator": "alice"}`（operator 由 controller 自动从 RequestContext.username 注入）
- **AND** 响应 200

#### Scenario: 单组件部署版本查询

- **GIVEN** team `default`，组件 alias `my-app`
- **WHEN** `GET /console/teams/default/apps/my-app/deploy-version`
- **THEN** kuship 调 region `GET /v2/tenants/default/services/my-app/deployversions`
- **AND** 响应 200 + `data.bean.deploy_version` 来自 region 透传

#### Scenario: 团队批量部署版本查询

- **GIVEN** team `default`，service_ids `[svc-1, svc-2, svc-3]`
- **WHEN** `POST /console/teams/default/deploy-version` body=`{"service_ids": ["svc-1", "svc-2", "svc-3"]}`
- **THEN** kuship 调 region `POST /v2/tenants/default/deployversions` body 透传
- **AND** 响应 200 + `data.bean` 含 `{svc-1: "tag1", svc-2: "tag2", svc-3: "tag3"}` map（透传 region）
- **AND** 不在 console 层做 service-level 缓存（决策 5：留作 hardening change `add-component-list-deploy-version-cache`）

#### Scenario: 异步源码检测发起

- **GIVEN** team `default`，组件 alias `my-app`，body 含 `source_type=sourcecode` / `repository_url=https://github.com/foo/bar.git` / `branch=main`
- **WHEN** `POST /console/teams/default/apps/my-app/source-check`
- **THEN** kuship 调 region `POST /v2/tenants/default/servicecheck` body 透传
- **AND** 响应 200 + `data.bean.check_uuid` 来自 region（前端用此 uuid 后续轮询）
- **AND** console 层 NOT 做 `build_strategy` 推断（cnb / slug / dockerfile 自动选择保留在 rainbond-console 行为不变）

#### Scenario: 源码检测结果查询

- **GIVEN** check_uuid `abc-123-def`
- **WHEN** `GET /console/teams/default/apps/my-app/source-check/abc-123-def`
- **THEN** kuship 调 region `GET /v2/tenants/default/servicecheck/abc-123-def`
- **AND** 响应 200 + `data.bean.check_status` 来自 region（值域 `checking` / `success` / `failure`）

#### Scenario: 多语言版本列表查询（slug 策略）

- **GIVEN** enterprise `ent-1`，region `rainbond`
- **WHEN** `GET /console/enterprise/ent-1/regions/rainbond/lang-version?lang=java&show=true&build_strategy=slug`
- **THEN** kuship 调 region `GET /v2/cluster/langVersion?language=java&show=true&build_strategy=slug`
- **AND** 响应 200 + `data.list` 来自 region 透传（含 `lang` / `version` / `event_id` / `is_allowed` / 等字段）

#### Scenario: 多语言版本创建

- **WHEN** `POST /console/enterprise/ent-1/regions/rainbond/lang-version` body=`{"lang":"java","version":"21.0.2","event_id":"evt-1","file_name":"java-21.tar.gz","build_strategy":"slug","is_allowed":true}`
- **THEN** kuship 调 region `POST /v2/cluster/langVersion` body 透传
- **AND** 响应 200

#### Scenario: 多语言版本删除（DELETE with body）

- **WHEN** `DELETE /console/enterprise/ent-1/regions/rainbond/lang-version` body=`{"lang":"java","version":"21.0.2","build_strategy":"slug"}`
- **THEN** kuship 调 region `DELETE /v2/cluster/langVersion` body 透传（DELETE with body 模式）
- **AND** 响应 200

#### Scenario: CNB framework 列表

- **WHEN** `GET /console/enterprise/ent-1/regions/rainbond/cnb/frameworks?lang=nodejs`
- **THEN** kuship 调 region `GET /v2/cluster/cnb/frameworks?lang=nodejs`
- **AND** 响应 200 + `data.list` 透传

#### Scenario: 非法 lang 参数

- **WHEN** `GET /console/enterprise/ent-1/regions/rainbond/lang-version?lang=cobol&show=true`
- **THEN** 响应 400 + `msg_show=不支持的语言`，未发起任何 region 调用

#### Scenario: 批量启停组件（改造后）

- **GIVEN** team `default`，3 个组件 service_id `[svc-1, svc-2, svc-3]` 全部属于该 team
- **WHEN** `POST /console/teams/default/batch_actions` body=`{"action":"start","service_ids":["svc-1","svc-2","svc-3"]}`
- **THEN** kuship 调 region `POST /v2/tenants/default/batchoperation` body=`{"operation":"start","service_ids":[{"service_id":"svc-1"},{"service_id":"svc-2"},{"service_id":"svc-3"}],"operator":"<current_username>"}` + Header `Resource-Validation: true`
- **AND** 响应 200 + `data.bean.success` / `data.bean.failed` 形状（从 region `batch_result` 数组解构而来）
- **AND** URL `/batch_actions` 与权限码 `APP_OVERVIEW_PERMS` 不变（向后兼容）

#### Scenario: 批量操作 service 不属于 team

- **GIVEN** team `default`，service_id `svc-x` 属于另一个 team
- **WHEN** `POST /console/teams/default/batch_actions` body=`{"action":"start","service_ids":["svc-x"]}`
- **THEN** 响应 403 + `msg_show=部分组件不属于当前团队`，未发起任何 region 调用

#### Scenario: region 5xx 透传

- **GIVEN** region 端在 `/v2/tenants/default/services/my-app/build-list` 返 503
- **WHEN** `GET /console/teams/default/apps/my-app/build-versions`
- **THEN** 响应 503 + general_message 形状（msg / msg_show 来自 region），HTTP status code 等于 region code

#### Scenario: 改造的 batch_actions 不自动降级

- **GIVEN** region 端 `/v2/tenants/default/batchoperation` 返 404（旧版 region 不支持）
- **WHEN** `POST /console/teams/default/batch_actions` body=`{"action":"start","service_ids":["svc-1"]}`
- **THEN** 响应 404 + `msg_show` 来自 region，**NOT** 自动降级到 N 次 `ServiceLifecycleOperations.startService` 循环
- **AND** ERROR 日志记录 region 端不支持 batchoperation，提示运维通过 region 升级修复

#### Scenario: 与 maven-setting 子 change 的边界

- **GIVEN** 本 change 已落地，`LangVersionOperations` 5 method + URL `/console/enterprise/{eid}/regions/{rn}/lang-version` GET/POST/PUT/DELETE 已可用
- **WHEN** `migrate-console-maven-setting`（P2）子 change 后续要落地 maven setting + 复用 lang version 读端点
- **THEN** 该子 change SHALL 引用本 change 落地的 `LangVersionOperations.getLangVersion`，**NOT** 重写
- **AND** maven-setting 子 change 的新接口 `MavenSettingOperations` 的 5 method 仅承载 maven-specific 端点，不与 lang version CRUD 重叠

#### Scenario: 本地 entity schema 校验

- **GIVEN** kuship-console 启动，hibernate `ddl-auto=validate`
- **WHEN** Spring Data JPA 加载 `ServiceBuildVersion` / `LangVersion` entity
- **THEN** 启动成功（schema 一致），entity 的 21 列 / 10 列与 region Go 端 `service_build_version` / `lang_version` 表的列名 / 类型完全匹配
- **AND** 若 schema 不一致（rainbond 未升级到对应版本），启动失败并给出明确的字段差异错误信息
