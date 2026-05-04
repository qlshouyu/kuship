## ADDED Requirements

### Requirement: 集群（region）生命周期管理

kuship-console SHALL 提供 enterprise 级别的集群增删改查端点，路径完全沿用 rainbond `/console/enterprise/{enterprise_id}/regions`：

- `GET /` —— 列出 enterprise 内所有 region；可选 query 参数 `status` 与 `check_status`
- `POST /` —— 添加新集群：解析 kubectl-format YAML token，落 `region_info` 表
- `GET /{region_id}` —— 集群详情
- `PUT /{region_id}` —— 修改集群（alias / desc / url / cert）
- `DELETE /{region_id}` —— 删除集群（前提：无 team 在该 region 上有 namespace 绑定）

写入端点 SHALL 要求 `@RequireEnterpriseAdmin`；GET 列表/详情对所有已认证用户开放。

#### Scenario: 添加集群解析 YAML token

- **WHEN** `POST /console/enterprise/ent-1/regions`，body `{"region_name":"r1","region_alias":"R1","desc":"测试","token":"<yaml>","region_type":["public"]}`
- **AND** YAML 内容包含 `ca.pem`、`client.pem`、`client.key.pem`、`apiAddress`、`websocketAddress`、`defaultDomainSuffix`、`defaultTCPHost` 全部 7 个字段
- **THEN** 写入 `region_info` 表新行，自动生成 `region_id`（UUID 36 字符）；响应 `code=200`、`data.bean` 含完整 region 详情

#### Scenario: 添加集群 token 缺字段触发 400

- **WHEN** YAML 缺 `ca.pem` 字段
- **THEN** 响应 `code=400`、`msg_show="CA 证书不存在"`、`msg="ca.pem not found"`

#### Scenario: 列出 enterprise 集群

- **WHEN** 已登录用户 `GET /console/enterprise/ent-1/regions`
- **THEN** 响应 `data.list` 为该 enterprise 下所有 region；不含敏感字段（cert_file/key_file 在 level=safe 下被脱敏）

#### Scenario: 删除有 team 在用的集群拒绝

- **WHEN** 删除 `region_id=r1`，但 `tenant_region` 表中存在 `region_name="r1"` 的关联
- **THEN** 响应 `code=400`、`msg_show="该集群仍有团队在使用，请先关闭团队的集群关联"`

#### Scenario: 删除集群后 evict region client cache

- **WHEN** 成功删除某 region
- **THEN** 服务端 SHALL 调用 `RegionClientFactory.evict(enterpriseId, regionName)` 立即失效缓存的 RestClient

### Requirement: 集群 License 端点

kuship-console SHALL 提供集群授权管理端点（路径 `/console/enterprise/{enterprise_id}` 下）：

- `GET /licenses` —— enterprise 维度的 license summary
- `GET /regions/{region_name}/license/cluster-id` —— 拿当前集群的 cluster-id
- `POST /regions/{region_name}/license/activate` —— 提交 license 激活
- `GET /regions/{region_name}/license/status` —— 当前授权状态

实际授权数据由 region API 转发；console 仅做 pass-through + 错误码透传。激活端点 SHALL 要求 `@RequireEnterpriseAdmin`。

#### Scenario: 拿 cluster-id

- **WHEN** `GET /console/enterprise/ent-1/regions/r1/license/cluster-id`
- **THEN** kuship-console 调用 region API（通过 `ClusterOperations.getClusterId`）；响应 `data.bean.cluster_id` 为 region 返回的字符串

#### Scenario: License 激活成功

- **WHEN** admin `POST /.../license/activate` body `{"license":"<base64-license>"}`
- **THEN** kuship-console 转发给 region API，region 验证通过返回 `code=200`，kuship 透传

#### Scenario: License 激活失败的业务码透传

- **WHEN** region 返回 `code=10400`（InvalidLicense）
- **THEN** `RegionApiResponseProcessor` 抛 `InvalidLicenseException`，由 `GlobalExceptionHandler` 包装为 general_message 响应（`code=10400`、`msg_show="授权码无效"`）

### Requirement: 团队-集群关联（开通/查询）

kuship-console SHALL 提供 team-region 关联管理端点（路径 `/console/teams/{team_name}/region`）：

- `GET /query` —— 当前 team 已开通的集群（`tenant_region` 表中该 team 的所有行）
- `GET /unopen` —— 当前 team 未开通的集群（enterprise 全集 - 已开通集合）
- `POST /` —— 开通集群

开通集群 SHALL 写 `tenant_region` 表 + 调 `TenantOperations.createTenant` 在 region 侧建 namespace；任一步失败均 rollback（事务包裹）。开通端点 SHALL 要求 `@RequirePerm("team_region_install")`，查询端点 SHALL 要求 `@RequirePerm("team_region_describe")`。

#### Scenario: 开通集群

- **WHEN** team admin `POST /console/teams/alpha/region` body `{"region_name":"r1"}`
- **THEN** 写入 `tenant_region` 行（`tenant_id=alpha.tenantId`、`region_name=r1`、`is_active=1`）；调用 `TenantOperations.createTenant(r1, ent-1, ...)` 在 region 侧建 namespace；响应 `code=200`

#### Scenario: 重复开通幂等

- **WHEN** team 已开通某 region，再次 POST 开通
- **THEN** 不重复写 row，响应 `code=200` + 提示 `msg_show="已开通该集群"`

#### Scenario: region 创建 namespace 失败 rollback

- **WHEN** 开通过程中 `TenantOperations.createTenant` 抛 RegionApiException
- **THEN** `tenant_region` 行不写入；异常透传给上层

### Requirement: Region 元信息查询端点

kuship-console SHALL 提供以下 region 元数据查询端点：

- `GET /console/regions` —— 全局 region 列表（简版，不含 cert）
- `GET /console/teams/{team_name}/regions/{region_name}/publickey` —— 集群公钥
- `GET /console/teams/{team_name}/regions/{region_name}/features` —— 集群 feature flag 列表
- `GET /console/teams/{tenant_name}/protocols` —— 协议枚举（HTTP/TCP/gRPC 等）

`features` / `publickey` 路径 SHALL 转发给 region API（通过 `ClusterOperations.getRegionFeatures` / `TenantOperations.getRegionPublickey`）。

#### Scenario: 查询集群 feature

- **WHEN** `GET /console/teams/alpha/regions/r1/features`
- **THEN** kuship-console 调 region 取得 feature 列表（如 `["TCP-LB","KUBEBLOCKS-ENABLED"]`），透传 `data.list`

#### Scenario: 查询集群公钥

- **WHEN** `GET /console/teams/alpha/regions/r1/publickey`
- **THEN** 响应 `data.bean.public_key` 为 region 返回的 PEM 公钥字符串

### Requirement: 命名空间与资源查询端点

kuship-console SHALL 提供集群 namespace / resource 查询端点：

- `GET /console/teams/cluster/namespaces` —— 当前用户上下文集群的所有 namespace（用于"绑定已有 namespace"场景）
- `GET /console/enterprise/{enterprise_id}/regions/{region_id}/namespace` —— enterprise 维度的 namespace 列表（含 content 过滤）
- `GET /console/enterprise/{enterprise_id}/regions/{region_id}/resource` —— region 资源汇总（CPU/内存使用率）
- `GET /console/enterprise/{enterprise_id}/regions/{region_id}/tenants` —— 集群里所有 tenant 的资源占用
- `POST /console/enterprise/{enterprise_id}/regions/{region_id}/tenants/{tenant_name}/limit` —— 设置 tenant 资源上限

所有端点 SHALL 转发给 region API（通过 `ClusterOperations`）。set-limit 端点 SHALL 要求 `@RequireEnterpriseAdmin`。

#### Scenario: 列出集群 namespaces

- **WHEN** `GET /console/teams/cluster/namespaces`
- **THEN** kuship-console 调 region API `GET /v2/cluster/namespaces`，透传 `data.bean` 为 namespace 数组

### Requirement: 平台级镜像仓库凭据（Hub Registry，复用 team_registry_auths 表）

kuship-console SHALL 提供平台级镜像仓库凭据管理端点（路径 `/console/hub/registry`）。

**实现注意**：rainbond `HubRegistryView` 实际复用 `team_registry_auths` 表（通过 `tenant_id=''` + `region_name=''` 区分平台级），不是独立表。kuship 端 SHALL 同样复用单一 `TeamRegistryAuth` entity，平台级写入时强制 `tenantId=""` + `regionName=""`。

- `GET /` —— 列表
- `POST /` —— 新增
- `PUT /` —— 修改（body 含 secret_id 定位）
- `DELETE /?secret_id={id}` —— 删除
- `GET /image` —— 列出仓库镜像

写入端点 SHALL 要求 sys_admin（`RequestContext.sysAdmin=true`）；查询端点对所有认证用户开放。

#### Scenario: sys_admin 添加平台 registry

- **WHEN** sys_admin `POST /console/hub/registry`，body `{"domain":"docker.io","username":"u","password":"p","hub_type":"docker","secret_id":"<uuid>"}`
- **THEN** 写入 `team_registry_auths` 表，`tenant_id=""` 且 `region_name=""` 标识为平台级

#### Scenario: 普通用户无权修改

- **WHEN** 非 sys_admin `POST /console/hub/registry`
- **THEN** 响应 `code=403`、`msg_show="您无操作此功能的权限"`

#### Scenario: GET /image 调 registry HTTP API

- **WHEN** `GET /console/hub/registry/image?secret_id=x`
- **THEN** kuship-console 用该 secret 凭据调 registry 的 `/v2/_catalog` 端点；响应 `data.list` 为镜像名数组；调用失败时返回 `data.list=[]` + warning 日志

### Requirement: 团队级镜像仓库凭据（Team Registry Auth）

kuship-console SHALL 提供团队级镜像仓库凭据管理端点（路径 `/console/teams/{team_name}/registry/auth`）：

- `GET /` `POST /` —— 列表 / 新增
- `GET /{secret_id}` `PUT /{secret_id}` `DELETE /{secret_id}` —— 单条 RUD

所有端点 SHALL 要求 `@RequirePerm("team_registry_auth")`。表名为 `team_registry_auths`（注意末尾 `s`，rainbond 历史拼写）。

#### Scenario: team admin 添加 registry auth

- **WHEN** team admin `POST /console/teams/alpha/registry/auth`
- **AND** body `{"hub_type":"docker","domain":"private.io","username":"u","password":"p","region_name":"r1"}`
- **THEN** 写入 `team_registry_auths` 表，`secret_id` 自动生成（UUID 32 字符）

### Requirement: ClusterOperations 6 method 完整实现

kuship-console SHALL 在已有的 `ClusterOperations` 接口骨架（migrate-console-region-client）上完整实现以下 6 method：

- `getClusterId(regionName, enterpriseId)` → `GET /v2/cluster/cluster-id`
- `activateLicense(regionName, enterpriseId, licenseBody)` → `POST /v2/cluster/license-activate`
- `getLicenseStatus(regionName, enterpriseId)` → `GET /v2/cluster/license-status`
- `getRegionFeatures(regionName, tenantName)` → `GET /v2/cluster/features`
- `getRegionNamespaces(regionName, enterpriseId, content)` → `GET /v2/cluster/namespaces`
- `getRegionResources(regionName, enterpriseId)` → `GET /v2/cluster/resource`

实现 SHALL 用 `RegionClientFactory.getClient(regionName, enterpriseId)` 拿 RestClient，遵循 `TenantOperationsImpl` 的 `exchange + RegionApiResponseProcessor` 模式，错误自动映射为 RegionApiException 族。

#### Scenario: getClusterId 转发成功

- **WHEN** `clusterOperations.getClusterId("r1", "ent-1")`，region 返回 `{"code":200,"data":{"bean":{"cluster_id":"abc"}}}`
- **THEN** 返回 `"abc"`

#### Scenario: 网络异常透传

- **WHEN** region 不可达（连接被拒）
- **THEN** 抛 `RegionApiSocketException`，由 `GlobalExceptionHandler` 包装为 503 + `msg_show="集群网络异常"`

### Requirement: RegionInfo entity 与现有 RegionInfoDto 共存

kuship-console SHALL 引入 `cn.kuship.console.modules.region.entity.RegionInfo`（JPA `@Entity`）+ `RegionInfoEntityRepository`（继承 `JpaRepository`）用于业务层 CRUD；同时保留 `cn.kuship.console.infrastructure.region.repository.RegionInfoRepository`（`JdbcTemplate` 只读）服务于 `RegionClientFactory`，两者共享同一张 `region_info` 表。

#### Scenario: 业务层用 JPA entity

- **WHEN** controller 注入 `RegionInfoEntityRepository.findByRegionName("r1")`
- **THEN** 返回 `Optional<RegionInfo>` JPA entity

#### Scenario: infrastructure 层用 JdbcTemplate

- **WHEN** `RegionClientFactory` 内部装配 RestClient
- **THEN** 仍调用 `RegionInfoRepository.findByEnterpriseAndName(eid, name)` 拿 `RegionInfoDto`，不进入 hibernate session

#### Scenario: 写入路径仅走 JPA

- **WHEN** controller 通过 `regionInfoEntityRepo.save(region)` 写入或更新
- **THEN** Hibernate 写入数据库；JdbcTemplate 端立刻可见（同 connection pool）

## MODIFIED Requirements

### Requirement: region_info 表只读访问

kuship-console SHALL 通过双层访问 `region_info` 表：

1. **infrastructure 层只读**：`infrastructure/region/repository/RegionInfoRepository`（基于 `JdbcTemplate`）保留只读路径；用 `RegionInfoDto` 输出。该路径专供 `RegionClientFactory` 装配 mTLS RestClient 使用，**不引入 hibernate session 副作用**（避免 region client 调用时触发 lazy-loading）。
2. **业务层读写**：`migrate-console-region-cluster` 起新增 `modules/region/entity/RegionInfo` JPA `@Entity` + `RegionInfoEntityRepository`，承载 enterprise/regions CRUD 端点的写入路径。

两者共享同一张 `region_info` 表；schema 演进权仍归 rainbond-console。

#### Scenario: 按 region_name 查询（infrastructure 层）

- **WHEN** 调用 `regionInfoRepository.findByName("region-1")`
- **THEN** 返回 `Optional<RegionInfoDto>` 含 `regionId/regionName/url/wsurl/sslCaCert/certFile/keyFile/enterpriseId` 等字段（snake_case 列名映射到 camelCase Java 字段）

#### Scenario: 不存在返回空（infrastructure 层）

- **WHEN** 调用 `regionInfoRepository.findByName("not-exist")`
- **THEN** 返回 `Optional.empty()`

#### Scenario: 业务层 JPA entity 写入

- **WHEN** controller `regionInfoEntityRepo.save(new RegionInfo(...))`
- **THEN** Hibernate INSERT/UPDATE 该行，符合 `region_info` schema（21 列含 region_id/region_name/url/wsurl/cert/...）；同 connection pool 视图下 JdbcTemplate 立刻可见

#### Scenario: 删除 region 时强制 evict client cache

- **WHEN** 业务层 `regionInfoEntityRepo.delete(region)` 后
- **THEN** 服务层 SHALL 显式调 `RegionClientFactory.evict(enterpriseId, regionName)`，否则缓存里的旧 RestClient 仍可用于已删 region

### Requirement: Region API 客户端基础设施

kuship-console SHALL 提供 `cn.kuship.console.infrastructure.region` 模块作为 Region API 客户端基础设施：包含 `RegionClientFactory`（按 enterpriseId+regionName 缓存 RestClient）、`RegionInfoRepository`（JdbcTemplate 只读 DTO）、`KeyStoreFactory` / `SslContextFactory`（PEM 内联构造 PKCS12 KeyStore）、`RegionApiResponseProcessor`（13 个 RegionApiException 族映射）、`RegionErrorMsgEnricher`（Helm 冲突 + 域名冲突 + 频繁操作短语汉化）。

`RegionClientFactory.evict(enterpriseId, regionName)` SHALL 是 public method，业务层删除 region 后 SHALL 显式调用使缓存失效。

`migrate-console-region-cluster` 起，14 个域接口（`TenantOperations` / `ClusterOperations` 等）开始按需补完业务实现：本 change 完成 `ClusterOperations` 6 method（getClusterId / activateLicense / getLicenseStatus / getRegionFeatures / getRegionNamespaces / getRegionResources）；其他业务 change（如 `migrate-console-app-create`）补完各自所需的 method。

#### Scenario: 按 (enterpriseId, regionName) 缓存 RestClient

- **WHEN** 业务 service 多次调 `regionClientFactory.getClient("ent-1", "r1")`
- **THEN** 仅首次构造 RestClient + KeyStore；后续调用从内存缓存返回同一实例

#### Scenario: evict 失效缓存

- **WHEN** 调用 `regionClientFactory.evict("ent-1", "r1")`
- **THEN** 缓存中该 key 的 RestClient 被移除；下次 `getClient` 重新构造（重新读 region_info、装配 mTLS）

#### Scenario: 14 接口骨架按 change 渐进补完

- **WHEN** 业务 change 落实 `ClusterOperations.getClusterId(...)`
- **THEN** 在 `cn.kuship.console.modules.region.api.ClusterOperationsImpl`（`@Primary @Service`）中实现，覆盖原 default 占位 bean
