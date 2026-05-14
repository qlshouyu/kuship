## ADDED Requirements

### Requirement: 批量组件依赖与旧版卷依赖

kuship-console SHALL 落地 `ServiceDependencyOperations` 接口中既存的 3 个 default unsupported method（`addDependencies` 批量 / `addVolumeDependency` 旧版 / `deleteVolumeDependency` 旧版），并补齐 1 个新的批量加依赖 endpoint（`POST /console/teams/{team_name}/apps/{service_alias}/dependency-list`）；旧版 `volume-dependency` 仅提供 region 调用 method，不暴露 console controller URL（rainbond 5.0+ 前端已不直调）。

业务规则：

- 批量加依赖 MUST 走两阶段写：本地 INSERT `tenant_service_relation` → region `addDependencies` → region 失败事务回滚本地行
- 已存在的 `(service_id, dep_service_id)` MUST 跳过去重（不抛错），与 rainbond 行为一致
- 循环依赖（A→B→A）MUST 在 service 层检测，抛 `ServiceHandleException(400, "circular dependency", "依赖关系不能形成循环")`
- region 路径 `/dependencys` 拼写 MUST 保留（rainbond 历史拼写错），与 region API 严格一致
- region body MUST 注入 `tenant_id`（取 `Tenants.namespace`），与 rainbond `tenant_region.region_tenant_id` 规约一致
- `addVolumeDependency` / `deleteVolumeDependency` 仅供后续 helm-install / app-import 子 change 内部调用，本 change 不在 console URL 暴露
- 写端点 `@RequirePerm("manage_team_app")` 或 fallback `app_create_perms`

#### Scenario: 批量加依赖全部新

- **GIVEN** 组件 svc1 当前无任何依赖，3 个待加 dep dep1/dep2/dep3 均为新
- **WHEN** `POST /console/teams/default/apps/svc1/dependency-list` body=`{"dep_service_ids":["dep1","dep2","dep3"]}`
- **THEN** 本地 `tenant_service_relation` 新增 3 行
- **AND** region API `addDependencies` 调用 1 次，body 含 3 个 dep_service_ids + tenant_id
- **AND** 响应 200 + region 响应

#### Scenario: 批量加依赖部分已存在跳过

- **GIVEN** 组件 svc1 已依赖 dep1，提交 `["dep1","dep2"]`
- **WHEN** 同上 endpoint
- **THEN** 本地仅新增 dep2 一行（dep1 跳过）
- **AND** region API 调用 1 次，body 仍含 `["dep1","dep2"]`（去重在本地，region 端可能也去重）

#### Scenario: 批量加依赖含循环

- **GIVEN** 已存在 svc1→svc2 依赖，提交 svc2 加 dep `[svc1]`
- **WHEN** `POST /apps/svc2/dependency-list` body=`{"dep_service_ids":["svc1"]}`
- **THEN** 抛 `ServiceHandleException(400, "circular dependency", "依赖关系不能形成循环")`
- **AND** 响应 400
- **AND** 本地 0 行新增，region 0 调用

#### Scenario: 批量加依赖 region 失败回滚

- **GIVEN** 本地 INSERT 3 dep 完成，region 返 5xx
- **WHEN** 同上
- **THEN** 事务回滚，本地 `tenant_service_relation` 撤销 3 行
- **AND** 响应 5xx + region 原始错误

#### Scenario: 旧版 volume-dependency region method 不暴露 controller

- **GIVEN** 本 change 已落地
- **WHEN** client 直接 `POST /console/teams/default/apps/svc1/volume-dependency`
- **THEN** 响应 404（无对应 controller）
- **AND** `addVolumeDependency` region method 仍可被 helm-install / app-import 子 change 内部调用，无需 console URL

#### Scenario: 写端点权限校验

- **GIVEN** 普通团队成员（无 `manage_team_app` 权限）
- **WHEN** `POST /apps/svc1/dependency-list`
- **THEN** 响应 403
