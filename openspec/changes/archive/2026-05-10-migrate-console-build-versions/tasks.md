# Tasks — migrate-console-build-versions

## 1. 校验既有接口与依赖

- [ ] 1.1 确认 `infrastructure/region/api/ServiceOperations.java` 当前持有 7 个 method（`createService` / `getServiceInfo` / `updateService` / `deleteService` / `buildService` / `codeCheck` / `getServiceLanguage`），全部已被 `ServiceOperationsImpl` 覆盖；本 change 在此接口上 **追加** 9 个 default unsupported method 声明
- [ ] 1.2 grep 确认 kuship-console 中**未**出现 `LangVersionOperations` / `BatchServiceOperations` 接口（避免冲突）；`AppBatchActionsController` 已存在于 `modules/appruntime/controller/`（本 change 改造而非新建）
- [ ] 1.3 grep 确认 kuship-console 中**未**出现 `ServiceBuildVersion` / `LangVersion` entity（避免冲突）
- [ ] 1.4 用 docker exec mysql 执行 `DESC service_build_version` / `DESC lang_version` 拉真实 schema 并写入 `design.md` 决策 2 / 决策 3 字段表（如 region Go 端列名与本 change 决策表不一致，以真实 schema 为准；写入"实施期 schema 校验"小节）
- [ ] 1.5 探测 region 端：用本地 8080 console 已注入的 RegionClient，curl 各 region 真实节点验证 15 个 URL（**实施期联动**，无在线 region 实例时推迟到 task §8）：
  - `GET /v2/tenants/<ns>/services/<alias>/build-list` → 期望 200 + bean.list 含构建版本
  - `GET /v2/tenants/<ns>/services/<alias>/build-version/<vid>` → 200 + bean
  - `PUT` 同 URL → 200
  - `DELETE` 同 URL → 200
  - `GET /v2/tenants/<ns>/services/<alias>/deployversions` → 200 + bean.deploy_version
  - `POST /v2/tenants/<ns>/deployversions` body `{"service_ids":[...]}` → 200 + bean map
  - `POST /v2/tenants/<ns>/servicecheck` body `{...}` → 200 + bean.check_uuid
  - `GET /v2/tenants/<ns>/servicecheck/<uuid>` → 200 + bean.check_status
  - `GET /v2/tenants/<ns>/plugin/<pid>/build-version/<bv>` → 200 + bean
  - `GET /v2/cluster/langVersion?language=java&show=true` → 200 + list
  - `POST /v2/cluster/langVersion` → 200
  - `PUT` 同 URL → 200
  - `DELETE` 同 URL → 200
  - `GET /v2/cluster/cnb/frameworks?lang=nodejs` → 200 + list
  - `POST /v2/tenants/<ns>/batchoperation` Header `Resource-Validation: true` → 200 + batch_result
- [ ] 1.6 把 1.5 探测结果写进 `design.md`"实施期探测结果"小节（实施期联动验证留 task §8）

## 2. 新建本地 entity 与 repository

- [ ] 2.1 新建 `modules/application/entity/ServiceBuildVersion.java`：21 列按决策 2 字段表映射；`@Entity @Table(name = "service_build_version")`；PK `Integer ID`（列名大写）；`finish_time` 列允许 NULL（rainbond `0001-01-01T00:00:00Z` sentinel 在 region 序列化层处理，本地表用 NULL）；`commit_msg` / `code_commit_msg` 同时存在（rainbond 历史兼容字段，不合并）
- [ ] 2.2 新建 `modules/application/repository/ServiceBuildVersionRepository.java` extends `JpaRepository<ServiceBuildVersion, Integer>`：仅 finder（`findByServiceId(serviceId)` / `findByServiceIdAndBuildVersion(serviceId, buildVersion)` / `findFirstByServiceIdOrderByBuildVersionDesc(serviceId)`），**无 writer**
- [ ] 2.3 新建 `modules/application/entity/LangVersion.java`：10 列按决策 3 字段表映射；`@Entity @Table(name = "lang_version")`；PK `Integer ID`；`show` / `first_choice` / `is_allowed` 用 `Boolean`（hibernate 自动映射 tinyint(1)）
- [ ] 2.4 新建 `modules/application/repository/LangVersionRepository.java` extends `JpaRepository<LangVersion, Integer>`：仅 finder（`findByLang(lang)` / `findByLangAndVersion(lang, version)`），无 writer
- [ ] 2.5 启动 console 后用 hibernate `validate` 模式确认两张表 schema 校验通过（表不存在时给出明确错误信息，由用户在 rainbond 环境下手动确认 schema 已存在）

## 3. 扩 ServiceOperations 接口 + 实现 9 method

- [ ] 3.1 编辑 `infrastructure/region/api/ServiceOperations.java`，在 7 个既有 method 之后追加 9 个 default unsupported 声明：
  - `getBuildVersions(rn, tn, alias)` returns `Map<String, Object>`
  - `getBuildVersionById(rn, tn, alias, vid)` returns `Map<String, Object>`
  - `updateBuildVersion(rn, tn, alias, vid, body)` returns `Map<String, Object>`
  - `deleteBuildVersion(rn, tn, alias, vid, body)` returns `void`
  - `getServiceDeployVersion(rn, tn, alias)` returns `Map<String, Object>`
  - `getTeamServicesDeployVersion(rn, tn, body)` returns `Map<String, Object>`
  - `getServiceCheckInfo(rn, tn, uuid)` returns `Map<String, Object>`
  - `serviceSourceCheck(rn, tn, body)` returns `Map<String, Object>`
  - `getBuildStatus(rn, tn, plugin_id, build_version)` returns `Map<String, Object>`
  - 每个 method 的 javadoc 标注 rainbond `regionapi.py` 锚点行号 + 实施 change `migrate-console-build-versions`
- [ ] 3.2 在 `ServiceOperationsImpl`（`@Primary`）追加 9 个 override，全部走 `RegionApiSupport.exchange` + `processor.extractBean / checkStatus`：
  - URL 拼装严格按决策 1 表格
  - `tenantName` 路径段沿用 `encode(tenantName)` 不主动改 namespace（与既有 7 method 保持一致，决策 1 已说明）
  - DELETE with body 用 `c.method(HttpMethod.DELETE).uri(url).contentType(JSON).body(body)` 模式
  - `updateBuildVersion` 与 `deleteBuildVersion` 共享同一 URL 但 body 语义不同（前者含 `plan_version`，后者含 `operator`），不复用代码
- [ ] 3.3 单测 `ServiceOperationsImplVersionTest`（11 用例）：
  - 9 method × 1 happy path（MockRestServiceServer 断言 URL / method / body）
  - 1 用例：`updateBuildVersion` body 透传 `plan_version` 字段断言
  - 1 用例：region 503 透传（任一 method）

## 4. 新建 LangVersionOperations 接口 + 实现

- [ ] 4.1 新建 `modules/application/api/LangVersionOperations.java`：5 method + 同 `ServiceOperations` 风格声明 default unsupported（接口常量 `IMPLEMENTING_CHANGE = "migrate-console-build-versions"`）
  - `getLangVersion(eid, rn, lang, show, build_strategy)` returns `Map<String, Object>`
  - `createLangVersion(eid, rn, body)` returns `Map<String, Object>`
  - `updateLangVersion(eid, rn, body)` returns `Map<String, Object>`
  - `deleteLangVersion(eid, rn, body)` returns `Map<String, Object>`
  - `getCnbFrameworks(eid, rn, lang)` returns `Map<String, Object>`
- [ ] 4.2 新建 `modules/application/api/LangVersionOperationsDefaultImpl.java` `@Service` 占位 bean
- [ ] 4.3 新建 `modules/application/api/LangVersionOperationsImpl.java` `@Primary @Service`：
  - URL 拼装按决策 1 表格 + 决策 7（接口签名带 `enterpriseId` 但 URL 不输出）
  - `getLangVersion` query 参数：`?language={lang}&show={show}` + `build_strategy` 非空时追加 `&build_strategy={strategy}`
  - `getCnbFrameworks` query：`?lang={lang}`（默认 `nodejs` 由 controller 兜底）
  - `deleteLangVersion` DELETE with body 模式（决策 8）
- [ ] 4.4 单测 `LangVersionOperationsImplTest`（7 用例）：
  - 5 method × happy
  - `getLangVersion` 含 `build_strategy=cnb` 与不带 `build_strategy` 两 case，断言 URL 拼接正确
  - region 5xx 透传

## 5. 新建 BatchServiceOperations 接口 + 实现

- [ ] 5.1 新建 `modules/application/api/BatchServiceOperations.java`：1 method
  - `batchOperationService(rn, tn, body)` returns `Map<String, Object>`
- [ ] 5.2 新建 `modules/application/api/BatchServiceOperationsDefaultImpl.java` `@Service` 占位
- [ ] 5.3 新建 `modules/application/api/BatchServiceOperationsImpl.java` `@Primary @Service`：
  - URL = `/v2/tenants/{tenant_name}/batchoperation`
  - 显式 `.header("Resource-Validation", "true")`（与 rainbond Python `_set_headers(token, resource_validation="true")` 一致）
  - body 透传 controller 入参；`Map<String, Object>`
  - 透传 region 响应（含 `batch_result` 数组）
- [ ] 5.4 单测 `BatchServiceOperationsImplTest`（3 用例）：
  - happy path 断言 URL + Header `Resource-Validation: true` 在
  - body 透传断言（`operation` / `service_ids` 等字段进入 region 调用 body）
  - region 503 透传

## 6. 新建 3 个 controller

- [ ] 6.1 新建 `modules/application/controller/version/AppVersionsController.java`：
  - `@GetMapping({".../build-versions", ".../build-versions/"})` —— 列表（query 参数 `page` / `page_size`）
  - `@GetMapping({".../build-versions/{version_id}", ".../"})` —— 详情
  - `@PutMapping({".../build-versions/{version_id}", ".../"})` —— 更新（body 含 `plan_version`）
  - `@DeleteMapping({".../build-versions/{version_id}", ".../"})` —— 删除（body `{"operator": <username from RequestContext>}` 由 controller 自动注入）
  - `@GetMapping({".../deploy-version", ".../"})` —— 单查
  - `@PostMapping({".../source-check", ".../"})` —— 异步发起源码检测
  - `@GetMapping({".../source-check/{uuid}", ".../"})` —— 查检测结果
  - `@GetMapping({".../build-status", ".../"})` —— query 参数 `plugin_id` + `build_version`
  - 全部 `@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)` 读 / `@RequirePerm(PermCode.APP_OVERVIEW_CONSTRUCT)` 写
  - 注入 `ServiceOperations` + `TenantServiceRepository` + `TenantsRepository`；不注入 `ServiceBuildVersionRepository`（本 change 不读本地表）
- [ ] 6.2 新建 `modules/application/controller/version/BatchDeployVersionController.java`：
  - `@PostMapping({"/console/teams/{team_name}/deploy-version", ".../"})`
  - body `@RequestBody Map<String, Object>` 含 `service_ids: [...]`
  - 调 `serviceOps.getTeamServicesDeployVersion(regionName, teamName, body)`
  - `regionName` 从 body 取或从 RequestContext 拿
  - `@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)`
- [ ] 6.3 新建 `modules/region/controller/lang/LangVersionController.java`：
  - `@GetMapping({"/console/enterprise/{enterprise_id}/regions/{region_name}/lang-version", ".../"})` —— query 参数 `lang` / `show` / `build_strategy`
  - `@PostMapping(...)` / `@PutMapping(...)` / `@DeleteMapping(...)` 同 path
  - `@GetMapping({"/console/enterprise/{enterprise_id}/regions/{region_name}/cnb/frameworks", ".../"})` —— query 参数 `lang`（默认 `nodejs`）
  - `@RequireEnterpriseAdmin`（lang version 是平台级配置，仅 enterprise admin 可写；读端点保留 admin 权限以保持一致性）
  - 注入 `LangVersionOperations`
- [ ] 6.4 全部 controller 路径变量统一 snake_case，trailing slash 双声明，不显式 ApiResult 包装（advice 自动）
- [ ] 6.5 controller 中本地 404 / 400 校验：
  - `team_name` 解析 → 不存在 404 + `msg_show=团队不存在`
  - `service_alias` 解析 → 不存在 404 + `msg_show=组件不存在`
  - lang 白名单 `{java, nodejs, python, go, php, ruby, dotnet, static, vm}` —— 非法 400
  - build_strategy 白名单 `{slug, cnb, ""}` —— 非法 400

## 7. 改造 AppBatchActionsController（与 batch_operation_service 接线）

- [ ] 7.1 编辑 `modules/appruntime/controller/AppBatchActionsController.java`：
  - 移除 `for (TenantService s : services)` 循环 lifecycle 调用
  - 改为单次 `batchOps.batchOperationService(regionName, teamName, body)`
  - body 形状转换：把入参 `{action, service_ids: [...]}` 改写为 region 期望的 `{operation, service_ids: [{service_id: id1}, ...], operator}`（按 action 类型分支：start/stop/restart 走 `service_ids` 数组，build/upgrade/deploy 走 `build_infos` 数组并附 `kind` / `code_version` / `image_info` 等额外字段）
  - 响应形状改造：把 region `{batch_result: [...]}` 解构为 `{success: [service_id1, ...], failed: [{service_id, msg}, ...]}`，**保持 UI 兼容期 0 改动**
- [ ] 7.2 注入 `BatchServiceOperations batchOps` 替代或并存 `ServiceLifecycleOperations lifecycle`（若保留 fallback 走 lifecycle 单调度，标 deprecated 6 个月警告期）
- [ ] 7.3 删除 `collectServiceIds` 中走 `serviceRepo.findAll()` 的 alias → service_id 反查路径（性能问题，全表扫描）；改为 `serviceRepo.findByServiceAliasIn(aliases)`（新增 finder 方法）
- [ ] 7.4 保留既有 controller 行为：URL `/console/teams/{team_name}/batch_actions` 不变；权限码 `APP_OVERVIEW_PERMS` 不变；body 入参 `{action, service_ids, service_alias_list, operator}` 不变；响应 `{success, failed}` 形状不变
- [ ] 7.5 兼容 region 端若不支持 `/batchoperation` 端点（旧版 region）：region 4xx 时**不**自动降级到 N 次 lifecycle 循环（决策：透传错误，运维通过 region 升级修复；自动降级会让 UI 行为漂移）；记 ERROR 日志 + 透传 region 错误

## 8. SecurityConfig

- [ ] 8.1 11 个新 endpoint 全部走默认 JWT 鉴权链，**无需** permitAll；本 change 不修改 SecurityConfig
- [ ] 8.2 改造的 `AppBatchActionsController` 路径不变，SecurityConfig 不需要任何调整

## 9. 集成测试

实施合并到 4 个集成测试类：

- [ ] 9.1 `AppVersionsControllerTest`（10 用例，`@SpringBootTest + @MockitoBean ServiceOperations + JdbcTemplate seed Tenants/TenantService`）：
  - 列表 happy（断言 `data.bean.list` 来自 region.list 透传）
  - 详情 happy
  - 更新 happy（PUT body 透传 `plan_version`，断言 `serviceOps.updateBuildVersion` 被调）
  - 删除 happy（断言 `serviceOps.deleteBuildVersion` 被调，body 含 `operator` = current user nick_name）
  - deploy-version 单查 happy
  - source-check 异步发起 happy（POST，断言响应含 `data.bean.check_uuid`）
  - source-check 查询 happy（GET by uuid）
  - build-status happy（query `plugin_id` + `build_version`）
  - service 不存在 → 404
  - region 503 → 503 透传
- [ ] 9.2 `BatchDeployVersionControllerTest`（3 用例）：
  - happy（service_ids 数组透传）
  - service_ids 为空 → 400
  - region 503 透传
- [ ] 9.3 `LangVersionControllerTest`（7 用例）：
  - GET 含 `build_strategy=slug` happy
  - GET 不含 `build_strategy` happy
  - POST 创建 happy
  - PUT 更新 happy
  - DELETE 删除 happy（断言 DELETE with body）
  - GET cnb/frameworks happy
  - 非法 lang → 400
  - 非法 build_strategy → 400
- [ ] 9.4 `AppBatchActionsControllerRegressionTest`（6 用例，回归改造）：
  - URL `/batch_actions` 仍 200
  - body `{action: "start", service_ids: [...]}` → 内部转 `batchOps.batchOperationService(rn, tn, {operation: "start", service_ids: [{service_id: id1}, ...], operator: ...})`
  - 响应仍是 `{success: [...], failed: [...]}` 形状（断言 region `{batch_result: [...]}` 被正确解构）
  - region 5xx → 透传错误（**不**自动降级到 lifecycle 循环）
  - 非法 action → 400
  - service 不属于 team → 403

## 10. 文档与归档

- [ ] 10.1 更新 `kuship-console/CLAUDE.md` 在"集群基础信息透传（migrate-console-cluster-extras）"段后追加"构建版本与多语言版本管理（migrate-console-build-versions）"段：
  - 列 3 controller + 1 改造 controller
  - 15 region method 路径
  - 2 新接口（`LangVersionOperations` / `BatchServiceOperations`）的所属包路径
  - 2 张 entity 的 schema 真相段
  - 与 `migrate-console-maven-setting` 子 change 的边界（lang version 由本 change 拥有）
  - 与 `add-component-list-deploy-version-cache` hardening 的解耦边界
  - 同步更新接口表 `ServiceOperations` 行从 7/15 标 16/16 完成；`LangVersionOperations` / `BatchServiceOperations` 新增两行
- [ ] 10.2 路线图 `migrate-region-coverage-roadmap` Requirement 表中把 `migrate-console-build-versions` 行标注为已完成（归档时执行）+ 标注 `LangVersionOperations` / `BatchServiceOperations` 的所有权
- [ ] 10.3 `design.md` 的"实施期 schema 校验"小节 + "实施期探测结果"小节由 task §1.4 / §1.6 写入，便于后续 maven-setting 子 change 复用

## 11. 编译 / 重启 / 联动验证

- [ ] 11.1 `cd kuship-console && mvn -DskipTests package` 通过；同时 `mvn test` 在 build-versions 范围内全部新增用例（约 30+）通过
- [ ] 11.2 重启 console；`curl -s -H "Authorization: GRJWT $TOKEN" "http://localhost:8080/console/teams/default/apps/<alias>/build-versions" | jq .` 返 200 + `data.bean.list`（**需用户本地起 console + region 后联动**）
- [ ] 11.3 `curl ... /console/teams/default/apps/<alias>/deploy-version` 返 200 + `data.bean.deploy_version`（**需用户联动**）
- [ ] 11.4 `curl ... /console/teams/default/deploy-version -d '{"service_ids":["..."]}'` 返 200 + `data.bean` 含 service_id → tag 映射（**需用户联动**）
- [ ] 11.5 `curl ... /console/enterprise/$EID/regions/rainbond/lang-version?lang=java&show=true` 返 200 + `data.list`（**需用户联动**）
- [ ] 11.6 `curl ... /console/enterprise/$EID/regions/rainbond/cnb/frameworks?lang=nodejs` 返 200 + `data.list`（**需用户联动**）
- [ ] 11.7 改造后的 `POST /console/teams/default/batch_actions -d '{"action":"start","service_ids":["..."]}'` 返 200 + `data.bean.success/failed` 形状（**需用户联动**，验证 region `/batchoperation` 真实 dispatch + 响应解构）
- [ ] 11.8 不存在的 service_alias 详情 `/build-versions` → 404 透传，本地不缓存（**需用户联动**）
