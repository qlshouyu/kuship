## Why

经过前 5 个 change 落地，kuship-console 现已具备：账户/团队/RBAC、集群管理、Region API 客户端基础设施。但用户登录后**仍然看不到任何应用**——因为 `tenant_service`（组件）/ `service_group`（应用）这两张核心表还没有被任何 controller 触及。

`migrate-console-application-core` 是首个让用户真正"看到自己的应用"的 change：落地"应用 (group) 主体 + 6 类配置子资源（env / port / volume / dependency / probe / 域名简版）"，把"团队下创建/管理应用"这条主链路打通。**应用创建（source_code/image/compose/vm 等多种来源）独立到 `migrate-console-app-create` 落地**，本 change 仅做"已存在组件的查看与配置管理"。

rainbond 端体量较大（5685 LOC across `views/group.py` + `app/` + `app_config/` 子目录），本 change 严格聚焦"读取与配置编辑"，把"创建/启停/构建/监控/插件/域名/证书"等 punt 到对应业务 change。

## What Changes

### 应用（Group/Application）主体

- ADDED：`GroupController`（路径 `/console/teams/{team_name}/groups`）
  - `GET /` —— 列出 team 下所有 application（service_group 表）
  - `POST /` —— 创建空 application（仅 group 元数据，不含组件）
  - `GET /{app_id}` —— application 详情
  - `PUT /{app_id}` —— 修改 application（group_name / note / governance_mode / k8s_app）
  - `DELETE /{app_id}` —— 删除 application（前提：含组件需先删除）
  - `GET /{app_id}/status` —— application 整体状态（聚合所有组件状态）
  - `GET /{app_id}/component_names` —— 该 app 下所有组件的简版列表
  - `GET /{app_id}/governancemode` —— 治理模式
  - `PUT /{app_id}/governancemode` —— 修改治理模式
- ADDED：`ServiceGroup` entity（对应 `service_group` 表）+ Repository

### 服务/组件（Service/Component）查询与基础信息

- ADDED：`ComponentController`（路径 `/console/teams/{team_name}/apps/{service_alias}`）
  - `GET /detail` —— 组件完整详情（tenant_service 全字段）
  - `GET /brief` —— 简版字段
  - `GET /status` —— 组件运行状态（转发 region API）
  - `GET /group` —— 组件所属 application
  - `PUT /group` —— 修改组件归属（迁移到另一 application）
  - `GET /keyword` —— 组件 keyword（label）
- ADDED：`TenantService` entity（对应 `tenant_service` 表，~50 列）+ Repository

### 环境变量（envs）

- ADDED：`AppEnvController`（路径 `/console/teams/{team_name}/apps/{service_alias}/envs`）
  - `GET /` —— 列表（区分 inner/outer scope）
  - `POST /` —— 新增
  - `PUT /{env_id}` —— 修改
  - `DELETE /{env_id}` —— 删除
- ADDED：`TenantServiceEnvVar` entity（对应 `tenant_service_env_var` 表）+ Repository

### 端口（ports）

- ADDED：`AppPortController`（路径 `/console/teams/{team_name}/apps/{service_alias}/ports`）
  - `GET /` —— 列表
  - `POST /` —— 新增（写库 + 调 region API 同步）
  - `DELETE /{port}` —— 删除（写库 + 调 region API 同步）
  - `PUT /{port}` —— 修改 alias / inner-service / outer-service / k8s_service_name
- ADDED：`TenantServicesPort` entity（对应 `tenant_services_port` 表）+ Repository

### 存储卷（volumes）

- ADDED：`AppVolumeController`（路径 `/console/teams/{team_name}/apps/{service_alias}/volumes`）
  - `GET /` —— 列表
  - `POST /` —— 新增
  - `DELETE /{volume_id}` —— 删除
  - `PUT /{volume_id}` —— 修改 capacity / access-mode
- ADDED：`TenantServiceVolume` entity（对应 `tenant_service_volume` 表）+ Repository

### 依赖（dependency）

- ADDED：`AppDependencyController`（路径 `/console/teams/{team_name}/apps/{service_alias}/dependency` 与同级 `/dependency-list` `/dependency-reverse`）
  - `GET /dependency-list` —— 当前组件依赖的服务列表
  - `GET /dependency-reverse` —— 依赖当前组件的服务列表
  - `POST /dependency` —— 新增依赖（写 `tenant_service_relation`）
  - `DELETE /dependency/{dep_service_id}` —— 删除依赖
- ADDED：`TenantServiceRelation` entity（对应 `tenant_service_relation` 表）+ Repository

### 探针（probe）

- ADDED：`AppProbeController`（路径 `/console/teams/{team_name}/apps/{service_alias}/probe`）
  - `GET /` —— 列表（mode = liveness / readiness / startup）
  - `POST /` —— 新增 / 修改
  - `DELETE /{probe_id}` —— 删除
- ADDED：`ServiceProbe` entity（对应 `service_probe` 表）+ Repository

### Region API 扩展

- MODIFIED：`ServiceOperations`（已存在的 14 接口骨架之一）—— 实现 `getServiceStatus(regionName, tenantName, serviceAlias)` / `getServiceDetail(regionName, tenantName, serviceAlias)`
- MODIFIED：`ServiceEnvOperations`、`ServicePortOperations`、`ServiceVolumeOperations`、`ServiceDependencyOperations`、`ServiceProbeOperations` —— 各实现对应的 add/remove/update method（同步 region API），最简 method 集（不实现 batch / advanced）

### 不进入此 change（明确 punt）

- **应用创建（source_code/image/compose/vm/kubeblocks/3rd-party）** —— `migrate-console-app-create`（rainbond 端约 1500 LOC）
- **域名 / 证书 / 网关策略**（`app_domain.py` 1521 LOC + gateway/certificate）—— `migrate-console-app-gateway` 独立 change
- **插件配置**（`app_plugin.py` 413 LOC）—— `migrate-console-plugin`
- **应用启停 / 构建 / 升级 / 回滚 / scale** —— `migrate-console-app-runtime`
- **监控 / 日志 / pod / event** —— `migrate-console-app-runtime`
- **应用市场 / 分享 / 备份 / helm 安装** —— `migrate-console-app-market`
- **代码 / 镜像 / 语言版本 / dockerfile / build_strategy** —— `migrate-console-app-create`
- **operation-logs / config-group / k8s-resources / k8s-services** —— 部分留给 misc，部分留给 runtime
- **multi-create / clone / migrate / topological** —— `migrate-console-misc`
- **autoscaler / monitor-rule** —— `migrate-console-app-runtime`
- **3rd-party endpoint / health / updatekey** —— `migrate-console-app-create`

## Capabilities

### New Capabilities

无（不引入新 capability）

### Modified Capabilities

- `kuship-console-app`：在已有 44 requirements 基础上 ADDED 应用主体管理 / 6 类配置子资源端点 / Service & Group entity 等 requirements；MODIFIED `Region API 客户端基础设施`（明确 ServiceOperations / Service{Env,Port,Volume,Dependency,Probe}Operations 5+1 接口本 change 实现状态）

## Impact

- **新增 entity**：`ServiceGroup`、`TenantService`（巨表 ~50 列）、`TenantServiceEnvVar`、`TenantServicesPort`、`TenantServiceVolume`、`TenantServiceRelation`、`ServiceProbe` —— 7 张表全部纳入 JPA
- **共享 schema 演进**：rainbond 仍拥有这些表的演进权；本 change 不发任何 DDL
- **写操作两写一致性**：env / port / volume / dependency / probe 的写入 SHALL 同时写本地表 + 调 region API；任一失败则事务回滚（local rollback；region 端的反向回滚由 rainbond 历史习惯放弃，但本 change 在错误信息中明确告知运维"region 与 console 状态可能不一致，请检查 service_alias=xxx 的 K8s 资源"）
- **kuship-ui**：`services/app.js` / `services/api.js` 中的应用查看/配置编辑路径（envs/ports/volumes/dependency/probe）解锁；前端"组件配置"侧边栏所有 tab 可工作
