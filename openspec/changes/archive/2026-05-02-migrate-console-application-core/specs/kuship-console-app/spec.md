## ADDED Requirements

### Requirement: 应用（Group/Application）主体管理

kuship-console SHALL 提供应用主体的 CRUD 端点（路径 `/console/teams/{team_name}/groups`）。应用对应 rainbond `service_group` 表；一个 application 含 N 个组件（通过 `service_group_relation` 关联）。

- `GET /` —— 当前 team 下所有 application（不含组件详情，仅元数据）
- `POST /` —— 创建空 application
- `GET /{app_id}` —— 详情
- `PUT /{app_id}` —— 修改 group_name / note / governance_mode / k8s_app
- `DELETE /{app_id}` —— 删除（前提：无组件归属）
- `GET /{app_id}/status` —— 整体状态（聚合所有组件状态）
- `GET /{app_id}/component_names` —— 该 app 下所有组件简版列表
- `GET /{app_id}/governancemode` `PUT /{app_id}/governancemode` —— 治理模式

读取端点 SHALL 要求 `@RequirePerm("app_overview_describe")`；创建/修改/删除 SHALL 要求 `@RequirePerm("app_create_perms")`。

#### Scenario: 列出团队应用

- **WHEN** 已登录用户 `GET /console/teams/alpha/groups`
- **THEN** 响应 `data.list` 为该 team 下所有 application；每项含 `app_id` / `group_name` / `note` / `governance_mode` / `k8s_app` / `create_time`

#### Scenario: 创建空应用

- **WHEN** team admin `POST /console/teams/alpha/groups` body `{"group_name":"my-app","note":"test","region_name":"r1","k8s_app":"my-k8s-app"}`
- **THEN** 写入 `service_group` 表，`tenant_id=team.tenantId`，`is_default=false`，`order_index=0`，`app_type="rainbond"`；响应 `data.bean.app_id` 为新建主键

#### Scenario: 删除有组件的应用拒绝

- **WHEN** 删除某 application，`service_group_relation` 表中存在该 group_id 的关联
- **THEN** 响应 `code=400`、`msg_show="该应用下仍有组件，请先迁移或删除组件"`

### Requirement: 组件（Service/Component）查询与基础信息

kuship-console SHALL 提供组件查询端点（路径 `/console/teams/{team_name}/apps/{service_alias}`）：

- `GET /detail` —— 完整字段（tenant_service 全字段，敏感字段如 secret 脱敏）
- `GET /brief` —— 简版（service_id / service_alias / service_cname / k8s_component_name / image / version / state）
- `GET /status` —— 运行状态（转发 region API）
- `GET /group` —— 当前所属 application（通过 service_group_relation 反查）
- `PUT /group` —— 迁移到另一 application
- `GET /keyword` —— 组件 keyword

#### Scenario: 组件详情脱敏

- **WHEN** `GET /console/teams/alpha/apps/svc-1/detail`
- **THEN** 响应 `data.bean` 含组件全字段，但 `secret` 字段输出占位（如 `***16 chars***`），不暴露明文

#### Scenario: 迁移组件到另一应用

- **WHEN** `PUT /console/teams/alpha/apps/svc-1/group` body `{"app_id":99}`
- **THEN** `service_group_relation` 表中 service_id=svc-1 的行被更新，group_id 改为 99；事务包裹

### Requirement: 环境变量（envs）管理

kuship-console SHALL 提供环境变量 CRUD 端点（路径 `/console/teams/{team_name}/apps/{service_alias}/envs`）：

- `GET /` —— 列表（query 参数 `scope=inner|outer` 过滤）
- `POST /` —— 新增
- `PUT /{env_id}` —— 修改
- `DELETE /{env_id}` —— 删除

环境变量写入**仅写本地表**（不调 region API）—— 与 rainbond 一致：env 在组件下次启动 / 重新部署时由 region 端读 console DB 拉取。

读取要求 `@RequirePerm("app_overview_env")`；写入同。

#### Scenario: 新增 env

- **WHEN** `POST .../envs` body `{"name":"DB_HOST","attr_name":"DB_HOST","attr_value":"172.20.0.10","is_change":true,"scope":"inner"}`
- **THEN** 写入 `tenant_service_env_var`，响应 `data.bean.id` 为新建 ID

#### Scenario: 唯一性校验

- **WHEN** 新增的 attr_name 在同 service_id+scope 下已存在
- **THEN** 响应 `code=400`、`msg_show="环境变量名已存在"`

### Requirement: 端口（ports）管理

kuship-console SHALL 提供端口 CRUD 端点（路径 `/console/teams/{team_name}/apps/{service_alias}/ports`）：

- `GET /` —— 列表
- `POST /` —— 新增（先调 region API 同步 → 再写本地）
- `DELETE /{port}` —— 删除（同上）
- `PUT /{port}` —— 修改 alias / inner-service / outer-service / k8s_service_name

#### Scenario: 新增端口同步 region

- **WHEN** `POST .../ports` body `{"port":8080,"protocol":"http","port_alias":"WEB","is_inner_service":false,"is_outer_service":true}`
- **THEN** 先调 `ServicePortOperations.addPort(regionName, tenantName, serviceAlias, ...)` 同步 K8s Service；region 成功后写 `tenant_services_port` 表

#### Scenario: region 同步失败本地不写

- **WHEN** region API 失败抛 `RegionApiException`
- **THEN** 本地 `tenant_services_port` 表不写入；异常透传给前端（`code=region 业务码`，`msg_show=region 返回的中文消息）

#### Scenario: 启用 outer-service 调用 inner→outer 切换

- **WHEN** `PUT .../ports/8080` body `{"is_outer_service":true}` 且原值为 false
- **THEN** 调用 `ServicePortOperations.openOuter(...)` 并更新本地行

### Requirement: 存储卷（volumes）管理

kuship-console SHALL 提供存储卷 CRUD 端点（路径 `/console/teams/{team_name}/apps/{service_alias}/volumes`）：

- `GET /` —— 列表
- `POST /` —— 新增
- `DELETE /{volume_id}` —— 删除
- `PUT /{volume_id}` —— 修改 capacity / access-mode

写入策略：先 region API 后本地。

#### Scenario: 新增 volume

- **WHEN** `POST .../volumes` body `{"volume_name":"data","volume_type":"share-file","volume_path":"/data","volume_capacity":10,"access_mode":"RWX"}`
- **THEN** 调 `ServiceVolumeOperations.addVolume(...)` 同步 region，成功后写 `tenant_service_volume` 表

### Requirement: 依赖（dependency）管理

kuship-console SHALL 提供依赖管理端点（路径 `/console/teams/{team_name}/apps/{service_alias}`）：

- `GET /dependency-list` —— 当前组件依赖的服务
- `GET /dependency-reverse` —— 依赖当前组件的服务
- `POST /dependency` —— 新增依赖
- `DELETE /dependency/{dep_service_id}` —— 删除依赖

通过 `tenant_service_relation` 表（service_id ↔ dep_service_id）维护。

#### Scenario: 新增依赖

- **WHEN** `POST .../dependency` body `{"dep_service_id":"svc-2","dep_order":0}`
- **THEN** 调 `ServiceDependencyOperations.addDependency(...)` 同步；写 `tenant_service_relation` 行

#### Scenario: 反向查询

- **WHEN** `GET /console/teams/alpha/apps/svc-2/dependency-reverse`
- **THEN** 响应 `data.list` 为所有 `tenant_service_relation.dep_service_id="svc-2"` 的 service_id 集合

### Requirement: 探针（probe）管理

kuship-console SHALL 提供探针 CRUD 端点（路径 `/console/teams/{team_name}/apps/{service_alias}/probe`）：

- `GET /` —— 列表（mode = liveness / readiness / startup）
- `POST /` —— 新增 / 修改（同 mode 软去重：先 delete 同 service_id+mode → 再 insert）
- `DELETE /{probe_id}` —— 删除

写入：先 region API 后本地。

#### Scenario: 同 mode 软去重

- **WHEN** `POST .../probe` body `{"mode":"liveness","scheme":"http","path":"/","port":8080,...}`
- **AND** 该组件已存在 mode=liveness 的探针
- **THEN** 旧探针被删除（本地+region）；新探针写入；最终保持 mode=liveness 仅一条

### Requirement: 应用核心 7 张表的 JPA Entity

kuship-console SHALL 引入以下 7 个 `@Entity`：`ServiceGroup`（service_group）、`ServiceGroupRelation`（service_group_relation）、`TenantService`（tenant_service ~50 列）、`TenantServiceEnvVar`（tenant_service_env_var）、`TenantServicesPort`（tenant_services_port）、`TenantServiceVolume`（tenant_service_volume）、`TenantServiceRelation`（tenant_service_relation）、`ServiceProbe`（service_probe）。

`TenantService` entity 一次性映射全部 50+ 列（含 build/code/dockerfile 等不在本 change scope 的字段），避免后续 change 反复扩 entity；hibernate validate 启动时 schema 一次性通过。

#### Scenario: 启动 schema 校验通过

- **WHEN** kuship-console 启动连真实 console DB
- **THEN** `hibernate.ddl-auto=validate` 对 8 张新增 entity（service_group / service_group_relation / tenant_service / tenant_service_env_var / tenant_services_port / tenant_service_volume / tenant_service_relation / service_probe）全部通过

#### Scenario: TenantService 主键策略

- **WHEN** TenantService entity 主键映射 `ID` 列
- **THEN** Java 字段 `id` 是 `Integer`（INT 4 字节，对齐 Django INT），`@GeneratedValue(strategy=IDENTITY)`

### Requirement: ServiceOperations 与 5 子资源 Operations 实现

kuship-console SHALL 在已有的 6 个域接口（ServiceOperations / ServiceEnvOperations / ServicePortOperations / ServiceVolumeOperations / ServiceDependencyOperations / ServiceProbeOperations）上完整实现以下 method：

- `ServiceOperations`：`getServiceStatus(regionName, tenantName, serviceAlias)`、`getServiceDetail(...)`、`batchGetServicesStatus(...)`
- `ServiceEnvOperations`：env 同步通常本地优先（rainbond 行为），仅在 controller 提交"重启 / build"时 region 自取；本 change 无需实现 env 端 API（保持 default unsupported），controller 仅写本地
- `ServicePortOperations`：`addPort` / `deletePort` / `openOuter` / `closeOuter` / `openInner` / `closeInner` / `updatePort`
- `ServiceVolumeOperations`：`addVolume` / `deleteVolume` / `updateVolume`
- `ServiceDependencyOperations`：`addDependency` / `deleteDependency`
- `ServiceProbeOperations`：`addProbe` / `deleteProbe` / `updateProbe`

每个 Impl 类用 `@Primary @Service` 覆盖 default 占位 bean，沿用 `TenantOperationsImpl` 的 `RegionClientFactory + exchange + RegionApiResponseProcessor` 模式。

#### Scenario: ServicePortOperations.addPort 转发

- **WHEN** controller 调 `servicePortOperations.addPort("r1", "alpha", "svc-1", req)`
- **THEN** 实现走 `POST /v2/tenants/{tenantName}/services/{serviceAlias}/ports` region API；成功后返回 void；失败抛 RegionApiException

#### Scenario: 14 接口骨架进度更新

- **WHEN** 检查 `cn.kuship.console.modules` 包
- **THEN** 至少 ServiceOperations / ServicePortOperations / ServiceVolumeOperations / ServiceDependencyOperations / ServiceProbeOperations 5 个 Impl 类存在（@Primary @Service），每个含 ~5 个核心 method

## MODIFIED Requirements

### Requirement: Region API 客户端基础设施

kuship-console SHALL 提供 `cn.kuship.console.infrastructure.region` 模块作为 Region API 客户端基础设施：包含 `RegionClientFactory`（按 enterpriseId+regionName 缓存 RestClient）、`RegionInfoRepository`（JdbcTemplate 只读 DTO）、`KeyStoreFactory` / `SslContextFactory`（PEM 内联构造 PKCS12 KeyStore）、`RegionApiResponseProcessor`（13 个 RegionApiException 族映射）、`RegionErrorMsgEnricher`（Helm 冲突 + 域名冲突 + 频繁操作短语汉化）。

`RegionClientFactory.evict(enterpriseId, regionName)` SHALL 是 public method，业务层删除 region 后 SHALL 显式调用使缓存失效。

14 个域接口（`TenantOperations` / `ClusterOperations` / `ServiceOperations` 等）按业务 change 渐进补完业务实现：
- `migrate-console-region-client`：完成 `TenantOperations` 5 method
- `migrate-console-region-cluster`：完成 `ClusterOperations` 8 method
- **`migrate-console-application-core`**（本 change）：完成 `ServiceOperations` 3 method + `ServicePortOperations` ~7 method + `ServiceVolumeOperations` ~3 method + `ServiceDependencyOperations` ~2 method + `ServiceProbeOperations` ~3 method
- 其他业务 change（`migrate-console-app-create` / `migrate-console-app-runtime` 等）补完各自所需的 method。

#### Scenario: 按 (enterpriseId, regionName) 缓存 RestClient

- **WHEN** 业务 service 多次调 `regionClientFactory.getClient("ent-1", "r1")`
- **THEN** 仅首次构造 RestClient + KeyStore；后续调用从内存缓存返回同一实例

#### Scenario: evict 失效缓存

- **WHEN** 调用 `regionClientFactory.evict("ent-1", "r1")`
- **THEN** 缓存中该 key 的 RestClient 被移除；下次 `getClient` 重新构造（重新读 region_info、装配 mTLS）

#### Scenario: 14 接口骨架按 change 渐进补完

- **WHEN** 业务 change 落实 `ServicePortOperations.addPort(...)`
- **THEN** 在 `cn.kuship.console.modules.application.api.ServicePortOperationsImpl`（`@Primary @Service`）中实现，覆盖原 default 占位 bean
