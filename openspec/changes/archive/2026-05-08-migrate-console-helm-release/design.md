## Context

kuship-console 已经迁移了 account / app-create / application / appruntime / appmarket / region 等多个域，建立了一套稳定的迁移模板：

- **Region API 接口分层**：`infrastructure/region/api/<Domain>Operations.java`（接口）+ `<Domain>OperationsDefaultImpl.java`（占位 default）+ `modules/<module>/api/<Domain>OperationsImpl.java`（`@Primary` 真实实现）。
- **HTTP 调用模板**：`RegionApiSupport.exchange(factory, regionName, enterpriseId, apiType, url, httpMethod, caller)` —— 统一处理 socket retry / 异常包装；`RegionApiResponseProcessor.extractBean / extractList / checkStatus` 统一解析 region 后端响应。
- **响应自动包装**：`@RestController` 直接 `return Map / List / DTO`，由 `GeneralMessageResponseBodyAdvice` 包成 `general_message` shape；不需要手动 `Response(general_message(...))`。
- **路径变量**：保留 Django 原始 snake_case（`{team_name}` / `{region_name}` / `{release_name}`），由 `TenantContextInterceptor` 自动写入 `RequestContext.teamName` / `regionName`。
- **trailing slash**：每个 mapping 显式列出 `/path` + `/path/`（Spring 6 已不支持全局 trailing slash 匹配）。

rainbond-console 中 helm release 域的核心是 `console/views/team_resources.py:210-291` 的 5 个 view，依赖：
1. `region_api.{get_tenant_helm_releases, install_tenant_helm_release, preview_tenant_helm_chart, get_tenant_helm_release_detail, upgrade_tenant_helm_release, uninstall_tenant_helm_release, get_tenant_helm_release_history, rollback_tenant_helm_release}`（8 method，`www/apiclient/regionapi.py:3739-3805`）
2. `helm_release_source_repo.{save_or_update, list_by_releases, get_by_release, delete_by_release}`（独立本地表 `team_helm_release_source`）
3. `helm_repo.get_helm_repo_by_name`（已在 kuship-console `modules/appmarket/helm/repository/HelmRepoRepository.java` 中实现）
4. 5 个 helper：`get_team_resource_namespace` / `build_helm_install_body` / `enrich_helm_release_list` / `enrich_helm_release_detail` / `persist_helm_release_source`

`team_helm_release_source` 表 schema（rainbond-console Django 拥有，kuship-console 仅 validate）：PK `ID` (Integer auto_increment, 由 BaseModel 提供) / `team_name` varchar(64) / `region_name` varchar(64) / `namespace` varchar(128) / `release_name` varchar(128) / `source_type` varchar(32) / `repo_name` varchar(128) NULL / `repo_url` varchar(255) NULL / `chart_name` varchar(128) NULL / `chart_version` varchar(64) NULL / `values_yaml` TEXT NULL / `creator` varchar(64) NULL / `create_time` DateTime NULL / `update_time` DateTime NULL，唯一键 `(region_name, namespace, release_name)`。

约束（来自 `kuship-console/CLAUDE.md`）：
- `hibernate.ddl-auto=validate`，**任何环境都不允许 Hibernate 输出 DDL**；`team_helm_release_source` 已存在于 rainbond-console 数据库中
- `Tenants` 等共享 schema entity 的 PK 必须为 `Integer`（Django INT 4 字节），不要用 `Long`
- 不在 entity 上加 `@Version` 列（破坏 Django 端写入）
- URL 不使用 `server.servlet.context-path`，每个 controller 显式声明完整路径前缀

## Goals / Non-Goals

**Goals:**

- 让 `kuship-ui` 的 helm 应用市场页面在 kuship-console 后端下完整工作（列表 / 安装 / 预览 / 详情 / 升级 / 卸载 / 历史 / 回滚）。
- 与 rainbond-console DRF 行为完全等价：路径、动词、入参字段、响应 shape（`bean.list[].source_info`）、错误码 / HTTP 状态码、`source_type=store→repo` 的入参转换。
- `team_helm_release_source` 表跨 rainbond-console 与 kuship-console 双向兼容（读 Django 写入的记录、写出 Django 可读的记录）。
- 复用既有迁移模板：`HelmOperations` 接口扩充 +7 method、`HelmOperationsImpl` `@Primary` 实现、`RegionApiSupport.exchange` 模板、`RegionApiResponseProcessor` 解析。

**Non-Goals:**

- 不引入新的 region 后端接口（Go 端 `/v2/tenants/{tenant_name}/helm/releases*` 已存在）。
- 不重写 `team_helm_release_source` 表的 schema；不写 Flyway migration；不改字段类型。
- 不迁移 `console/urls/team_resources.py` 中的非 helm 路由（`ns-resources` / `resource-center` 等留给独立 change）。
- 不改 `kuship-ui` 前端代码；不变更前端调用契约。
- 不替换 rainbond-console（Python 端继续运行在 7070），不做双写一致性保障。
- 不引入 `kuship-console` 内的新顶层能力命名（仍归属 `kuship-console-app`）。
- 不引入 helm chart 校验 / 上传 / 模板生成（已由 `appmarket` 域承载）。

## Decisions

### Decision 1：controller 落在 `modules/team/`，而非 `modules/appmarket/`

rainbond-console 在 Django 中按 `team_resources` 划分（同时含 ns-resource / resource-center / helm release），是"团队资源视角"而非"应用市场视角"；helm chart 信息接口（chart-information / yaml / upload-chart）才属于 appmarket。kuship-console 已存在的 `modules/appmarket/helm/`（HelmRepoController / HelmAppController）只处理 chart 仓库管理，不处理 release。

**选择**：新建 `modules/team/controller/HelmReleasesController.java` + `service/HelmReleaseService.java`，与未来的 ns-resource / resource-center controller 同包域。`modules/team/` 目前只有 `package-info.java`，本 change 顺势落地该子域起点。

**备选**：放入 `modules/appmarket/helm/controller/`。**否决**：模糊了"应用市场（chart 仓库 + chart 模板）"和"集群运行时（release 实例）"的边界，未来 ns-resource / resource-center 的 controller 也无法复用。

### Decision 2：扩充已存在的 `HelmOperations` 接口而非新建 `HelmReleaseOperations`

`infrastructure/region/api/HelmOperations.java` 已经定义了 chart 相关 6 个 method。release 相关 method 调用同一组 `/v2/tenants/{tenant_name}/helm/*` 路由，逻辑同源；拆分会让 `HelmOperationsImpl` 与 `HelmReleaseOperationsImpl` 共享 `RegionApiSupport.exchange` 与 `API_TYPE = "helm"` 常量，造成同义双份。

**选择**：在 `HelmOperations` 接口中追加 7 个 method（list / install / preview / detail / upgrade / uninstall / history / rollback），全部 `default { unsupported(IMPLEMENTING_CHANGE) }`；`HelmOperationsImpl`（`@Primary`）追加实现。`IMPLEMENTING_CHANGE` 常量改为 `"migrate-console-helm-release"`。

**备选**：新建 `HelmReleaseOperations` 接口。**否决**：增加无意义的接口分裂；rainbond-console Python 端也是同一个 `RegionInvokeApi` 类承载所有 helm 调用，无需在 Java 端反向拆分。

### Decision 3：`team_helm_release_source` 表 entity 落在 `modules/team/entity/`

该表的语义是"team 范围内的 helm release 来源信息"（按 `team_name + region_name + namespace + release_name` 索引），与 controller 同子域；放在 `modules/team/entity/` 让"controller / service / entity / repository"四件套同包。

**选择**：`modules/team/entity/HelmReleaseSource.java` + `modules/team/repository/HelmReleaseSourceRepository.java`。PK `Integer`（与 Django INT 一致）。`values_yaml` 用 `@Lob` + `@Column(columnDefinition = "TEXT")` 映射。

**备选**：放入 `modules/appmarket/helm/entity/`。**否决**：与 Decision 1 一致 —— release 不属于 appmarket 子域。

### Decision 4：source_info 增强采用"批量查询 + 内存合并"

`enrich_helm_release_list` 在 Python 端 `helm_release_source_repo.list_by_releases(region, namespace, [name1, name2, ...])` 返回 `Dict[namespace/name -> record]` 后，循环 release 列表内存合并 `source_info`。该模式避免 N+1 query，列表大小可控（单团队 helm release 一般 < 50）。

**选择**：在 `HelmReleaseSourceRepository` 中提供 `findByRegionNameAndNamespaceAndReleaseNameIn(region, namespace, names)` 派生查询，service 层组装 `Map<String, HelmReleaseSource>` key 为 `namespace + "/" + release_name`。

**备选**：JOIN 在 region API 响应解析时同步增强。**否决**：region API 响应是 JSON 透传，引入 JOIN 会破坏 `RegionApiResponseProcessor.extractBean` 的通用性。

### Decision 5：`source_type=store→repo` 转换在 service 层

`build_helm_install_body` 当 `source_type=store` 且 `repo_name` 已知时，查 `helm_repo` 表把 `repo_url / username / password` 注入入参，并把 `source_type` 改写为 `repo` —— 这是 region 后端 Go 接口期望的格式。kuship-console `HelmRepoRepository`（已存在）提供 `findByRepoName`。

**选择**：`HelmReleaseService.buildInstallBody(rawBody, namespace)` 在调 region API 之前完成转换；`persistHelmReleaseSource` 在调 region 成功后落库（保留 raw `source_type` 用于前端"upgrade_mode" 判定）。

**备选**：把转换放到 `HelmOperationsImpl.installTenantHelmRelease` 内部。**否决**：region API 实现层应保持薄封装（只负责 HTTP 调用 + 响应解析），不承载业务规则。

### Decision 6：`getTeamResourceNamespace` 复用 `Tenants` 实体

rainbond-console 的 `view.tenant.namespace` 由 DRF 在 `TenantHeaderView.initial()` 阶段从 `Tenants` 表加载；fallback 是 `tenant.tenant_name`。kuship-console 的 `Tenants` entity 已包含 `namespace` 字段（`length=33`，与 Django 一致）。

**选择**：`HelmReleaseService.resolveNamespace(teamName)` 调 `TenantsRepository.findByTenantName`，先取 `namespace`，否则 fallback `tenant_name`；查不到则抛 `ServiceHandleException(404, "team not found", "团队不存在")`。

**备选**：从 `RequestContext` 直接读取（如果上下文已加载 namespace）。**否决**：当前 `RequestContext` 只持有 `teamName / regionName`，未加载 namespace；扩 `RequestContext` 属于独立改动。

### Decision 7：写两阶段策略 —— 先 region 后 console（与 rainbond 一致）

- **install**：调 region `installTenantHelmRelease` → 成功后 `persistHelmReleaseSource`（落 `team_helm_release_source` 行）；落库失败仅打 ERROR 日志，不向用户报错（与 rainbond-console `try/except` 行为一致 —— release 已经在集群中创建，落库失败是次要问题，前端可手动同步）。
- **upgrade**：同上（先调 region 升级，再 `save_or_update` `team_helm_release_source` 行）。
- **uninstall**：先调 region `uninstallTenantHelmRelease`（释放 K8s 资源），再 `helmReleaseSourceRepo.deleteByRelease`；删行失败打 ERROR 日志，不影响 200 返回。

**选择**：service 层用 `try { region调用 } / persist或delete try-catch日志` 二段式，不进数据库事务（region 调用本身就跨网络，事务无意义）。

### Decision 8：错误码 / HTTP 状态对齐

复用 kuship-console 已有 `align-error-http-status` 约定：业务异常 HTTP 状态码 = 业务 `code`（`ServiceHandleException(404,...)` → HTTP 404）；region 异常优先用 `httpStatus`，缺失退回 `code` 或 500；body 仍是 `general_message` shape。`RegionApiResponseProcessor` 已实现该规则，无需新增逻辑。

## Risks / Trade-offs

- **Risk**：rainbond-console（Django）与 kuship-console 同时写入 `team_helm_release_source` 表可能存在 race（用户半切到 kuship-ui，又在 rainbond-ui 上 install）。
  → **Mitigation**：表上 `(region_name, namespace, release_name)` 是逻辑唯一键；`save_or_update` 用"先 select 再 update/insert"语义；rainbond-console 的 `helm_release_source_repo.save_or_update` 已经是 `update_or_create` 模式 —— Java 端实现保持等价语义（`existing.ifPresentOrElse(update, insert)`），最坏情况是后写覆盖先写，业务可接受。

- **Risk**：region 后端响应 schema 与 rainbond-console 假设不一致（如 `bean.summary` 字段缺失），导致 `enrichHelmReleaseDetail` NPE。
  → **Mitigation**：所有 `bean / summary / list` 取值用 `Optional` + 默认空 Map / List 兜底（与 Python `(bean or {}).get("summary") or {}` 等价）；单测覆盖空 bean / 空 summary 两种边界。

- **Risk**：`build_helm_install_body` 改写 `source_type=store→repo` 后，前端"立即查询"时拿到的响应 `source_info.source_type` 是 `repo`，而非用户原始选择 `store`，影响"升级模式锁定"展示。
  → **Mitigation**：`persistHelmReleaseSource` 落库时保留**原始 raw 的 source_type**（`raw_body.get("source_type")` 而非 install_body）；`enrichHelmReleaseList` 读这条记录时返回的 `source_info.source_type` 即原始值；与 rainbond-console 行为一致（`team_resources.py:103-130` 的 `source_type = (raw_body.get("source_type") or "store")`）。

- **Trade-off**：选择"扩充 `HelmOperations`"而非"新建 `HelmReleaseOperations`" 让单接口 method 数从 6 升到 13；接口语义略宽（chart 信息 + release 实例），但避免接口分裂的副作用。

- **Trade-off**：`enrichHelmReleaseList` 单独一次 DB 查询给每次列表请求增加 ~5ms RTT；列表 release 数量大时（> 100）可考虑缓存，但不在本 change 范围。

## Migration Plan

- **部署**：本 change 落地后，kuship-console 启动时 Hibernate 会 validate `team_helm_release_source` 表 schema —— 该表必须先在 console 库存在（rainbond-console Django migration 已创建）。在干净环境（无 rainbond-console 历史数据）下需先 `python manage.py migrate` 一次以创建该表，再启动 kuship-console；否则启动失败。**Action**：在 README / 部署文档中提示该前置依赖（独立 PR）。

- **回滚**：本 change 仅新增代码与表读写，不修改既有接口；回滚通过 git revert 即可，无需数据迁移。回滚后访问 `/console/teams/.../helm/releases` 会回到原来的 500 状态（行为退化，但不破坏其他端点）。

- **验证（部署后）**：
  1. 在 kuship-ui 应用市场 → Helm 应用页面，确认列表请求 200，且 `data.list[].source_info` 字段存在。
  2. 安装一个 test chart（如 nginx），确认 `team_helm_release_source` 表新增一行；卸载后该行被删除。
  3. 在 rainbond-ui 上安装一个 chart，再切到 kuship-ui，确认列表正确显示该 release 的 `source_info`（验证 Django 写、Java 读的兼容性）。
  4. 切回 rainbond-ui，确认 Java 端写入的 release 在 Python 端列表中也带 `source_info`（双向兼容）。

## Open Questions

- 是否需要在 `RequestContext` 中预加载 `namespace`（避免每次 helm release 调用都查 `Tenants` 表）？—— **暂不做**，等 ns-resource / resource-center 也迁移时统一抽离。
- `team_helm_release_source.values_yaml` 字段是否需要加密存储？rainbond-console 当前是明文 TEXT。—— **保持明文**，与 Django 行为一致；加密属于独立安全 change。
