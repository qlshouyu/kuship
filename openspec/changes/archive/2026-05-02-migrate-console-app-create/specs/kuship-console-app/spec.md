## ADDED Requirements

### Requirement: 应用创建端点（image / source_code / third_party 3 种来源）

kuship-console SHALL 提供 3 种来源的组件创建端点（路径 `/console/teams/{team_name}/apps`）：

- `POST /docker_run` —— 基于镜像创建（最简）
- `POST /source_code` —— 基于 Git 仓库创建
- `POST /third_party` —— 创建第三方组件（外部 endpoint）

所有创建端点 SHALL 要求 `@RequirePerm("app_overview_create")`；service_id 由 console 用 `UuidGenerator.makeUuid()` 生成 32 字符 UUID；service_alias 默认 `"gr" + service_id[:6]`，前端可显式提供覆盖；写入策略：先 console DB 后 region API（事务包裹，region 失败本地回滚）。

#### Scenario: 基于镜像创建

- **WHEN** team admin `POST /console/teams/alpha/apps/docker_run` body `{"image":"nginx:latest","port":80,"cmd":"nginx -g 'daemon off;'","group_id":1,"region_name":"r1","service_cname":"my-nginx"}`
- **THEN** 写入 `tenant_service` 行（service_id 自动生成、service_origin="assistant"、service_source="docker_run"）+ `service_source` 行（image/cmd 留底）+ `service_group_relation` 行（关联 application）
- **AND** 调 `ServiceOperations.createService` 在 region 侧建 K8s deployment / service / configmap
- **AND** 响应 `data.bean.service_id` / `service_alias` / `k8s_component_name` 完整字段

#### Scenario: region 创建失败回滚

- **WHEN** console 写入成功但 region API 抛 `RegionApiException`
- **THEN** 事务 rollback —— `tenant_service` / `service_source` / `service_group_relation` 全部不写入；异常透传给前端

#### Scenario: 基于 Git 仓库创建

- **WHEN** `POST /console/teams/alpha/apps/source_code` body `{"git_url":"https://github.com/x/y.git","code_version":"main","build_strategy":"buildpack","language":"java","group_id":1,...}`
- **THEN** 写入 tenant_service + service_source（含 git_url / code_version / build_strategy / language）+ service_group_relation；调 region createService

#### Scenario: 第三方组件创建

- **WHEN** `POST /console/teams/alpha/apps/third_party` body `{"endpoints":[{"address":"172.20.0.99","port":3306}],"service_cname":"external-mysql","group_id":1}`
- **THEN** 写入 tenant_service（service_source="third_party"）+ service_source（extend_info 含 endpoints JSON）；不调 region createService（第三方组件不需要 K8s deployment）

#### Scenario: service_alias 冲突

- **WHEN** 用户显式传与已存在组件相同的 service_alias
- **THEN** unique constraint 触发 → Spring 转 `ServiceHandleException(400, msg_show="组件别名已存在")`

### Requirement: 创建前/后检查（check 异步链路）

kuship-console SHALL 提供组件检查端点（路径 `/console/teams/{team_name}/apps/{service_alias}`）：

- `POST /check` —— 触发对组件的代码检查（异步），返回 `check_uuid`
- `GET /get_check_uuid` —— 查询当前 `check_uuid`
- `PUT /check_update` —— 用 region 返回的推荐配置更新组件（语言 / 端口 / env / build_strategy）

#### Scenario: 触发检查

- **WHEN** `POST /console/teams/alpha/apps/gr123456/check`
- **THEN** 调 region API `POST /v2/tenants/{tenant}/code-check`，region 返回 check_uuid + event_id；console 把 `check_uuid` 写入 `tenant_service.check_uuid` 字段；响应 `data.bean.check_uuid`

#### Scenario: 查询检查 UUID

- **WHEN** `GET /console/teams/alpha/apps/gr123456/get_check_uuid`
- **THEN** 响应 `data.bean.check_uuid` 为 `tenant_service.check_uuid` 字段值（前端基于此轮询 region event 状态）

#### Scenario: 应用检查推荐配置

- **WHEN** `PUT /console/teams/alpha/apps/gr123456/check_update` body `{"language":"java","ports":[{"port":8080,"protocol":"http"}],"envs":[...]}`
- **THEN** 把 language / ports / envs 持久化到 service_source / tenant_services_port / tenant_service_env_var 表（事务包裹）

### Requirement: 应用构建与编译参数

kuship-console SHALL 提供构建相关端点：

- `POST /console/teams/{team_name}/apps/{service_alias}/build` —— 触发组件构建
- `GET /console/teams/{team_name}/apps/{service_alias}/code/branch` —— 列出 git 仓库可用分支
- `GET /console/teams/{team_name}/apps/{service_alias}/compile_env` —— 查询编译环境变量
- `PUT /console/teams/{team_name}/apps/{service_alias}/compile_env` —— 修改编译环境变量

build 端点 SHALL 调用 `ServiceOperations.buildService(...)` 触发 region 构建任务，返回 region 给的 `event_id`；前端轮询 event 状态由 app-runtime change 落地。

#### Scenario: 触发构建

- **WHEN** `POST /console/teams/alpha/apps/gr123456/build` body `{"event_id":"<可选>","kind":"build_from_source_code"}`
- **THEN** 调 region buildService；响应 `data.bean.event_id` 为 region 给的事件 ID
- **AND** `tenant_service.deploy_version` 写入新版本号；`update_time` 刷新

#### Scenario: 修改 compile_env

- **WHEN** `PUT .../compile_env` body `{"BUILD_OPTS":"-Xmx2g","JAVA_OPTS":"-server"}`
- **THEN** compile_env 持久化到 `tenant_service_env_var` 表（scope="build"）

### Requirement: 应用删除（软删除归档）

kuship-console SHALL 提供组件删除端点：

- `POST /console/teams/{team_name}/apps/{service_alias}/delete` —— 删除组件

删除策略：
1. 调 `ServiceOperations.deleteService(...)` 释放 region 端 K8s 资源
2. 写 `tenant_service_delete` 软删除归档行（保留所有历史字段 + delete_time + 操作人 user_id）
3. 删除 `tenant_service` / `service_source` / `service_group_relation` / 相关 envs / ports / volumes / dependency / probe 行（事务包裹）

#### Scenario: 正常删除

- **WHEN** team admin `POST /console/teams/alpha/apps/gr123456/delete`
- **THEN** region 释放成功 → 软删除归档 + 本地表清理 → 响应 `code=200`

#### Scenario: region 删除失败本地不动

- **WHEN** region 抛 RegionApiException
- **THEN** 本地未做任何修改；异常透传

### Requirement: 应用创建相关 2 张新 Entity

kuship-console SHALL 引入 2 个 `@Entity`：`ServiceSourceInfo`（对应 `service_source` 表，存放创建参数 git/image/dockerfile/build_strategy 等）+ `TenantServiceInfoDelete`（对应 `tenant_service_delete` 表，组件软删除归档）。

`tenant_service_delete` schema 与 `tenant_service` 类似 + 多 `delete_time` / `app_name` / `app_id` 字段；本 change 一次性映射所有列。

#### Scenario: 创建组件时同步写 service_source

- **WHEN** 任一 3 种创建端点写入新组件
- **THEN** `service_source` 表也写入对应行（service_id 关联，user_name/password 仅 source_code git 鉴权场景填）

#### Scenario: 启动 schema 校验通过

- **WHEN** kuship-console 启动连真实 console DB
- **THEN** hibernate validate 对 2 张新增 entity 全部通过

## MODIFIED Requirements

### Requirement: Region API 客户端基础设施

kuship-console SHALL 提供 `cn.kuship.console.infrastructure.region` 模块作为 Region API 客户端基础设施：包含 `RegionClientFactory`（按 enterpriseId+regionName 缓存 RestClient）、`RegionInfoRepository`（JdbcTemplate 只读 DTO）、`KeyStoreFactory` / `SslContextFactory`（PEM 内联构造 PKCS12 KeyStore）、`RegionApiResponseProcessor`（13 个 RegionApiException 族映射）、`RegionErrorMsgEnricher`（Helm 冲突 + 域名冲突 + 频繁操作短语汉化）。

`RegionClientFactory.evict(enterpriseId, regionName)` SHALL 是 public method，业务层删除 region 后 SHALL 显式调用使缓存失效。

14 个域接口（`TenantOperations` / `ClusterOperations` / `ServiceOperations` 等）按业务 change 渐进补完业务实现：
- `migrate-console-region-client`：完成 `TenantOperations` 5 method
- `migrate-console-region-cluster`：完成 `ClusterOperations` 8 method
- `migrate-console-application-core`：完成 `ServicePortOperations` 5 + `ServiceVolumeOperations` 3 + `ServiceDependencyOperations` 2 + `ServiceProbeOperations` 3 + `ServiceOperations.getServiceInfo`
- **`migrate-console-app-create`**（本 change）：完成 `ServiceOperations` 剩余 6 method（createService / updateService / deleteService / buildService / codeCheck / getServiceLanguage）
- 其他业务 change（`migrate-console-app-runtime` / `migrate-console-app-market` 等）补完各自所需的 method。

#### Scenario: 按 (enterpriseId, regionName) 缓存 RestClient

- **WHEN** 业务 service 多次调 `regionClientFactory.getClient("ent-1", "r1")`
- **THEN** 仅首次构造 RestClient + KeyStore；后续调用从内存缓存返回同一实例

#### Scenario: evict 失效缓存

- **WHEN** 调用 `regionClientFactory.evict("ent-1", "r1")`
- **THEN** 缓存中该 key 的 RestClient 被移除；下次 `getClient` 重新构造（重新读 region_info、装配 mTLS）

#### Scenario: ServiceOperations 6 method 落地

- **WHEN** application-create change 落实 `ServiceOperations.createService(...)` / `deleteService(...)` / `buildService(...)` 等
- **THEN** 在 `cn.kuship.console.modules.appcreate.api.ServiceOperationsImpl`（@Primary @Service）中实现，覆盖原 application-core change 的 partial 实现 bean
