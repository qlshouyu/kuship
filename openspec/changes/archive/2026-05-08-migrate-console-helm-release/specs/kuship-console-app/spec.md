## ADDED Requirements

### Requirement: Helm Release 列表与安装端点

kuship-console SHALL 暴露 `GET /console/teams/{team_name}/regions/{region_name}/helm/releases` 用于列出团队 namespace 下的 helm release，以及 `POST /console/teams/{team_name}/regions/{region_name}/helm/releases` 用于安装一个新的 helm release；两个端点 SHALL 同时支持 trailing slash 形式（`/path` 与 `/path/`）。响应体 SHALL 由 `GeneralMessageResponseBodyAdvice` 自动包装为 `general_message` shape，列表数据 SHALL 包含 `source_info` 增强字段（来自 `team_helm_release_source` 表）。

#### Scenario: 列表请求成功返回带 source_info 的 release 列表

- **WHEN** 已认证用户向 `GET /console/teams/{team_name}/regions/{region_name}/helm/releases` 发起请求
- **THEN** kuship-console 通过 `Tenants.namespace`（fallback `tenant_name`）解析出 namespace，并以 `GET /v2/tenants/{tenant_name}/helm/releases?namespace={ns}` 转发到 region 后端
- **AND** 用 `team_helm_release_source` 表中按 `(region_name, namespace, release_name in [...])` 命中的记录给响应 `data.bean.list[]` 每个元素注入 `source_info` 字段（包含 `source_type / repo_name / repo_url / chart_name / chart_version / upgrade_mode`）
- **AND** 返回 HTTP 200 和 `general_message` shape 响应

#### Scenario: 列表请求 namespace 解析失败时返回 404

- **WHEN** 请求中的 `team_name` 在 `tenants` 表不存在
- **THEN** kuship-console 抛 `ServiceHandleException(404, "team not found", "团队不存在")`，HTTP 状态码 404，响应体 `code=404`、`msg_show="团队不存在"`

#### Scenario: 安装请求 source_type=store 时自动转换为 repo

- **WHEN** 已认证用户向 `POST /console/teams/{team_name}/regions/{region_name}/helm/releases` 发起请求，请求体含 `source_type=store, repo_name=stable, chart_name=nginx`
- **THEN** kuship-console 查询 `helm_repo` 表得到 `repo_url / username / password`，并将入参改写为 `source_type=repo, repo_url=..., username=..., password=...`
- **AND** 用改写后的 body 调用 region 后端 `POST /v2/tenants/{tenant_name}/helm/releases`
- **AND** 调用成功后向 `team_helm_release_source` 表写入一行（`source_type` 保留**原始** `store` 而非转换后的 `repo`）
- **AND** 返回 HTTP 200 和安装结果

#### Scenario: 安装成功但 team_helm_release_source 落库失败时仍返回 200

- **WHEN** region 后端成功创建 release 但 `team_helm_release_source` 落库抛异常
- **THEN** kuship-console 仅打 ERROR 日志（含 trace_id），不向用户报错
- **AND** 返回 HTTP 200 和 region 响应中的 `bean`

### Requirement: Helm Chart 预览端点

kuship-console SHALL 暴露 `POST /console/teams/{team_name}/regions/{region_name}/helm/chart-preview`（含 trailing slash），转发到 region 后端 `POST /v2/tenants/{tenant_name}/helm/chart-preview`，用于在安装前预览 chart 渲染结果。请求体 SHALL 经过 `buildHelmInstallBody` 转换（与安装端点同源）。

#### Scenario: 预览请求成功返回渲染结果

- **WHEN** 已认证用户向 `POST /console/teams/{team_name}/regions/{region_name}/helm/chart-preview` 发起请求
- **THEN** kuship-console 解析 namespace 并经 `buildHelmInstallBody` 转换 body 后转发到 region 后端
- **AND** 透传 region 响应的 `bean`，HTTP 200，`general_message` shape

### Requirement: Helm Release 详情/升级/卸载端点

kuship-console SHALL 暴露 `GET/PUT/DELETE /console/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}`（含 trailing slash），分别对应详情查询、升级、卸载。详情响应 SHALL 通过 `enrichHelmReleaseDetail` 注入 `summary.source_info` 字段，并在 `team_helm_release_source.values_yaml` 非空时用本地存储覆盖 `summary.values`。

#### Scenario: 详情请求注入 source_info 与本地 values_yaml

- **WHEN** 已认证用户向 `GET /console/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}` 发起请求
- **THEN** kuship-console 转发到 region 后端 `GET /v2/tenants/{tenant_name}/helm/releases/{release_name}?namespace={ns}`
- **AND** 查询 `team_helm_release_source` 表中 `(region_name, namespace, release_name)` 唯一记录
- **AND** 将记录中的 `source_type / repo_name / chart_name / chart_version` 等字段以 `source_info` 注入响应的 `bean.summary`
- **AND** 当记录中的 `values_yaml` 非空时，用其覆盖 `bean.summary.values`
- **AND** 返回 HTTP 200

#### Scenario: 升级成功后 team_helm_release_source 同步更新（保留原始 source_type）

- **WHEN** 已认证用户向 `PUT /console/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}` 发起请求并附带新版本 chart 入参
- **THEN** kuship-console 经 `buildHelmInstallBody` 转换 body 后转发到 region 后端
- **AND** 调用成功后用 `save_or_update` 语义更新 `team_helm_release_source` 行（保留原始 `raw_body.source_type`，更新 `chart_version / values_yaml`）
- **AND** 返回 HTTP 200

#### Scenario: 卸载成功后 team_helm_release_source 行被删除

- **WHEN** 已认证用户向 `DELETE /console/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}` 发起请求
- **THEN** kuship-console 先调 region 后端 `DELETE /v2/tenants/{tenant_name}/helm/releases/{release_name}?namespace={ns}` 释放 K8s 资源
- **AND** region 调用成功后从 `team_helm_release_source` 表删除 `(region_name, namespace, release_name)` 行
- **AND** 删行失败仅打 ERROR 日志，不影响 HTTP 200 返回

#### Scenario: 卸载时 region 调用失败 team_helm_release_source 不被删除

- **WHEN** region 后端返回非 2xx
- **THEN** kuship-console 透传 region 错误（HTTP 状态对齐 region），且 `team_helm_release_source` 行保持不变

### Requirement: Helm Release 历史与回滚端点

kuship-console SHALL 暴露 `GET /console/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/history` 与 `POST /console/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/rollback`（含 trailing slash），透传 region 后端响应；rollback 请求体 SHALL 在缺失 `namespace` 字段时自动注入 team namespace。

#### Scenario: 历史请求透传 region 响应

- **WHEN** 已认证用户向 `GET /console/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/history` 发起请求
- **THEN** kuship-console 转发到 region 后端 `GET /v2/tenants/{tenant_name}/helm/releases/{release_name}/history?namespace={ns}`
- **AND** 透传响应 `bean`（不做 source_info 增强，与 rainbond-console 行为一致）
- **AND** 返回 HTTP 200

#### Scenario: 回滚请求体缺失 namespace 时自动补齐

- **WHEN** 已认证用户向 `POST /console/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/rollback` 发起请求，请求体仅含 `revision`
- **THEN** kuship-console 在 body 中注入 `namespace = <从 Tenants 解析的 namespace>`
- **AND** 转发到 region 后端 `POST /v2/tenants/{tenant_name}/helm/releases/{release_name}/rollback`
- **AND** 透传 region 响应

### Requirement: HelmOperations 域接口扩充

kuship-console SHALL 在 `cn.kuship.console.infrastructure.region.api.HelmOperations` 接口上追加 7 个 method：`getTenantHelmReleases / installTenantHelmRelease / previewTenantHelmChart / getTenantHelmReleaseDetail / upgradeTenantHelmRelease / uninstallTenantHelmRelease / getTenantHelmReleaseHistory / rollbackTenantHelmRelease`；这些 method 在接口层 SHALL 提供 `default { unsupported(IMPLEMENTING_CHANGE) }` 占位，由 `cn.kuship.console.modules.appmarket.helm.api.HelmOperationsImpl`（`@Primary`）覆盖为真实实现。`HelmOperations.IMPLEMENTING_CHANGE` 常量 SHALL 更新为 `"migrate-console-helm-release"` 以反映当前迁移归属。

#### Scenario: 默认实现抛 unsupported 异常

- **WHEN** 不存在 `@Primary` 实现 bean，仅 `HelmOperationsDefaultImpl` 注入
- **THEN** 调用任一新增 method 抛 `UnsupportedOperationException`，message 含 `IMPLEMENTING_CHANGE = "migrate-console-helm-release"`

#### Scenario: HelmOperationsImpl 实现转发到正确的 region URL

- **WHEN** `HelmOperationsImpl.getTenantHelmReleases("rainbond", "default", "default")` 被调用
- **THEN** 通过 `RegionApiSupport.exchange` 向 region 后端发起 `GET /v2/tenants/default/helm/releases?namespace=default`，且 `apiType="helm"`、`httpMethod="GET"`
- **AND** 用 `RegionApiResponseProcessor.extractBean` 解析响应为 `Map<String, Object>`

#### Scenario: 安装失败时 region 错误透传

- **WHEN** region 后端对 `POST /v2/tenants/{tenant}/helm/releases` 返回 HTTP 400 + `general_message` 错误体
- **THEN** kuship-console 抛 region API 异常，HTTP 状态码与 `code` 对齐 region（400），响应体 `msg_show` 透传 region 中文文案（含 `RegionErrorMsgEnricher` 已实现的 helm.sh annotation 中文化处理）

### Requirement: team_helm_release_source 表 JPA 映射

kuship-console SHALL 在 `cn.kuship.console.modules.team.entity.HelmReleaseSource` 提供 `team_helm_release_source` 表的 JPA entity 映射，PK 类型 `Integer`（与 Django INT 一致），不含 `@Version` 列；`values_yaml` 字段 SHALL 用 `@Column(columnDefinition = "TEXT")` 映射；entity SHALL 通过 `hibernate.ddl-auto=validate` 在所有 profile 下与 rainbond-console 已存在的 schema 保持一致，且 SHALL NOT 触发任何 DDL 输出。`HelmReleaseSourceRepository` SHALL 提供 `findByRegionNameAndNamespaceAndReleaseName / findByRegionNameAndNamespaceAndReleaseNameIn / deleteByRegionNameAndNamespaceAndReleaseName` 派生查询。

#### Scenario: 启动时 schema 校验通过

- **WHEN** kuship-console 在 `team_helm_release_source` 表存在的 console 库上启动
- **THEN** Hibernate `validate` 模式不报错，应用启动成功
- **AND** `kuship-console/src/main/resources/db/migration/` 下不存在该表的 Flyway migration 文件

#### Scenario: 双向兼容 rainbond-console 写入

- **WHEN** rainbond-console (Python/Django) 通过 `helm_release_source_repo.save_or_update` 写入一行
- **THEN** kuship-console 通过 `TeamHelmReleaseSourceRepository.findByRegionNameAndNamespaceAndReleaseName` 能正确读取该行（所有字段无类型/长度截断）
- **AND** 反向：kuship-console 写入的行也能被 Django ORM 完整读取

#### Scenario: 列表批量查询返回 Map<key, record>

- **WHEN** service 层调用 `findByRegionNameAndNamespaceAndReleaseNameIn("rainbond", "default", ["nginx", "redis"])`
- **THEN** 返回包含两条记录的 `List<HelmReleaseSource>`（如果都存在）
- **AND** service 层组装为 `Map<String, HelmReleaseSource>` 时 key 为 `"default/nginx"` / `"default/redis"`（namespace + "/" + release_name）
