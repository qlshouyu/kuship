## Why

用户从 rainbond-ui（http://localhost:7070）切到 kuship-ui（http://localhost:8000）后，访问应用市场 → Helm 应用页面时，前端发起的 `GET /console/teams/{team_name}/regions/{region_name}/helm/releases` 直接返回 HTTP 500 + `"No static resource ..."`。根因：kuship-console 完全未实现 helm release 域的任何 controller/region API，Spring 找不到 handler 后落到静态资源解析器再被全局异常兜底。该域是 helm 应用市场的核心交互（列表/安装/详情/升级/卸载/历史/回滚/预览），缺失会让 kuship-ui 的 helm 入口完全不可用，阻塞从 rainbond-ui 到 kuship-ui 的切换。

## What Changes

- **新增 5 个 controller endpoint（共 9 个 HTTP 方法）**，路径与 rainbond-console `console/urls/team_resources.py:30-39` 严格一致：
  - `GET/POST /console/teams/{team_name}/regions/{region_name}/helm/releases` — 列表 + 安装
  - `POST /console/teams/{team_name}/regions/{region_name}/helm/chart-preview` — 预览
  - `GET/PUT/DELETE /console/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}` — 详情/升级/卸载
  - `GET /console/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/history` — 历史
  - `POST /console/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/rollback` — 回滚
- **扩充 `HelmOperations` 域接口**：在 `infrastructure/region/api/HelmOperations.java` 增加 7 个 release 相关 method（list / install / preview / detail / upgrade / uninstall / history / rollback），由 `HelmOperationsImpl`（`@Primary`）统一实现，全部转发 region 后端 `/v2/tenants/{tenant_name}/helm/releases*`。
- **新增本地 JPA 表映射**：`team_helm_release_source`（rainbond-console 已有 schema：`console/migrations/0004_teamhelmreleasesource.py` + `0005_teamhelmreleasesource_values_yaml.py`，kuship-console 仅 `validate`），entity + repository 落在 `modules/team/`（与新增 controller 同包域）。该表用于"安装/升级时持久化 chart 来源、values_yaml、creator"，并在"列表/详情"返回时用 `source_info` 字段增强响应。
- **新增业务辅助逻辑**（迁移自 `console/views/team_resources.py:20-161` 中的 5 个 helper）：
  - `getTeamResourceNamespace(tenant)` — 优先取 `Tenants.namespace`，fallback `tenant_name`
  - `buildHelmInstallBody(raw, namespace)` — 当 `source_type=store` 时，按 `repo_name` 查 `helm_repo` 转换为 `source_type=repo` + 注入 `repo_url/username/password`
  - `enrichHelmReleaseList(bean, region, namespace)` / `enrichHelmReleaseDetail(...)` — 通过 `team_helm_release_source` 表给响应注入 `source_info` 字段
  - `persistHelmReleaseSource(...)` — 安装/升级成功后落库
- **不破坏既有契约**：复用 `GeneralMessageResponseBodyAdvice` 自动包装、`RequestContext`（`team_name` / `region_name`）、`TenantContextInterceptor`、`RegionApiResponseProcessor`、`RegionApiSupport.exchange` 模板。
- **路径变量名严格保留 snake_case**（`{team_name}` / `{region_name}` / `{release_name}`），不得驼峰化。

## Capabilities

### New Capabilities

无 —— 本 change 不创建新顶层能力。helm release 是 `kuship-console-app` 内部的一个迁移子域，与已迁移的 `migrate-console-app-market`、`migrate-console-application-core` 等 change 同级处理，沿用既定能力承载。

### Modified Capabilities

- `kuship-console-app`：新增 helm release 域 5 个 endpoint 的接口契约（路径、动词、入参、响应 shape、`source_info` 增强、`source_type=store→repo` 转换）；新增 `team_helm_release_source` 表的 JPA 校验 + 写入约束；扩充 `HelmOperations` 域接口（+7 method）。

## Impact

- **代码新增**（kuship-console）：
  - `modules/team/controller/HelmReleasesController.java`（5 endpoint，~9 HTTP 方法）
  - `modules/team/service/HelmReleaseService.java`（业务装配 + helper 迁移）
  - `modules/team/entity/HelmReleaseSource.java` + `repository/HelmReleaseSourceRepository.java`
  - `modules/team/dto/`（请求/响应 DTO 若干）
  - `infrastructure/region/api/HelmOperations.java`（接口扩 +7 method，default 占位）
  - `modules/appmarket/helm/api/HelmOperationsImpl.java`（`@Primary` 实现新增方法）
  - 单测：`HelmOperationsImplTest`、`HelmReleaseServiceTest`、`HelmReleasesControllerTest`
- **数据库**：`team_helm_release_source` 表已由 rainbond-console（Django）拥有 schema；kuship-console 仅 `ddl-auto=validate` 校验，不执行任何 DDL；不写 Flyway migration。
- **API 契约**：与 rainbond-console DRF 完全等价（路径、动词、`general_message` 响应 shape、错误码、`bean.source_info`）；前端 `kuship-ui` 无需改动即可工作。
- **跨服务**：region 后端 `/v2/tenants/{tenant_name}/helm/releases*` 接口已存在（Go 端），无需变更。
- **不影响**：rainbond-console（Python，仍可独立运行在 7070）；其他已迁移 change（appmarket / application / appruntime / account 等）。
