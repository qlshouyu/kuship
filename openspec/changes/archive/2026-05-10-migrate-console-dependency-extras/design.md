# Design — migrate-console-dependency-extras

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是 **P0 #7**，估 3 method（精算一致），工作量约 2 天。

依赖：无（与 volume-extras / third-party-runtime 并行）。

## Region API URL 表

| method                                       | HTTP   | 路径                                                              | rainbond 锚点              |
|----------------------------------------------|--------|-------------------------------------------------------------------|---------------------------|
| addDependencies(rn, tn, alias, body)         | POST   | `/v2/tenants/{namespace}/services/{alias}/dependencys`            | `regionapi.py:242-265`     |
| addVolumeDependency(rn, tn, alias, body)     | POST   | `/v2/tenants/{namespace}/services/{alias}/volume-dependency`      | `regionapi.py:811-820`     |
| deleteVolumeDependency(rn, tn, alias, body)  | DELETE | `/v2/tenants/{namespace}/services/{alias}/volume-dependency`      | `regionapi.py:822-832`     |

`namespace` 取自 `Tenants.namespace || tenant_name`。

`addDependencies` body rainbond 端会注入 `tenant_id = tenant_region.region_tenant_id`，本 change 同样在 service 层注入。

## Controller 路径锚点

| Controller                          | path                                                                | method | rainbond 锚点               |
|-------------------------------------|---------------------------------------------------------------------|--------|----------------------------|
| AppDependencyController（追加）      | `/console/teams/{team_name}/apps/{service_alias}/dependency-list`    | POST    | `urls.py:614` `AppDependencyViewList` POST |

trailing slash 兼容：每 endpoint 同时声明 `path` 与 `path/`。

## 决策 1 — `dependencys` 拼写保留

rainbond region 端路径是 `/dependencys`（拼写错），本 change **不**修复，与 region API 严格一致。controller 端 URL 用 `/dependency-list`（rainbond console 端命名），不暴露 region 历史拼写给客户端。

## 决策 2 — 批量两阶段写策略

rainbond `app_relation_service.py:add_service_dependencies` 行为：

1. 解析 body 中 `dep_service_ids`（list）
2. 循环检查每个 dep：
   - 存在性（`tenant_service_relation` 表）→ 已存在的跳过（不报错）
   - 循环依赖检测（A→B→A）→ 抛 `ServiceHandleException`
3. 本地批量 INSERT `tenant_service_relation`（剩余非重复且不循环的）
4. 调 region `add_service_dependencys` body=`{dep_service_ids:[...], tenant_id: ...}` 一次性下发
5. region 失败抛异常 → 事务回滚 step 3 的本地 INSERT

**决策**：kuship 端 `AppDependencyBatchService.addBatch` `@Transactional` 包步骤 2-5。step 5 region 失败让事务回滚自动撤销 step 3 的 INSERT。**不**在 catch 块里手动反向 region call（与 application-core single addDependency 行为一致）。

## 决策 3 — 循环依赖检测复用 application-core 既有 helper

application-core 的 `addDependency` 里已有循环依赖检测 helper（如 `checkCyclicDependency(serviceId, depServiceId)`）。本 change 在批量 method 内**循环调用**该 helper，而非重写。

如果 application-core 未导出该 helper，本 change 可在 `AppDependencyBatchService` 里调本地的 `serviceRelationRepository.findAllByTenantId(...)` 自行做 BFS 检测。任务 4.2 选择更优方案。

## 决策 4 — `volume-dependency` 旧版无 controller URL

rainbond 5.0+ 的 console UI 已用新版 `depvolumes`，旧版 `volume-dependency` 路径在 `urls.py` 中**没有**对应 console URL（前端不直调）。本 change 仅实现 region method，给以下场景内部调用：

- `migrate-console-helm-install`（helm chart 模板里引用旧版 region API 字段时）
- `migrate-console-app-import-export`（旧版应用包里 dep_volumes 字段为旧格式时）

本 change 不接 helm-install / app-import 子 change，只把 method 接口实现好，留给后续。

## 决策 5 — 错误处理 + 权限

- 3 region method 透传 `RegionApiException`
- 循环依赖抛 `ServiceHandleException(400, "circular dependency", "依赖关系不能形成循环")`
- 已存在依赖**不**抛错（rainbond 行为：跳过去重）
- 批量 endpoint `@RequirePerm("manage_team_app")` 或 fallback `app_create_perms`

## 非决策（明确不做）

- **不**修复 rainbond region 路径拼写 `dependencys`
- **不**为 `volume-dependency` 新增 controller URL（旧版前端已不调）
- **不**实现"批量删除依赖"（rainbond 也没有）

## 测试约定

- `ServiceDependencyOperationsImplExtraTest`：3 method × (1 happy + 1 5xx)；断言 `dependencys` 拼写
- `AppDependencyBatchServiceTest`：
  - 批量加 3 dep，全部新 → INSERT 3 行 + region 调用 1 次
  - 批量加 3 dep，1 已存在 → INSERT 2 行 + region 调用 1 次（含 3 dep_service_ids，去重在本地不在 region body）
  - 批量加 含循环 → 抛异常，本地 0 行 INSERT
  - region 5xx → 事务回滚，本地 0 行
- `AppDependencyControllerBatchTest`：POST `/dependency-list` body=`{"dep_service_ids":[...]}` happy path + 权限校验
