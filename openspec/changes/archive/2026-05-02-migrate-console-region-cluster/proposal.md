## Why

`migrate-console-account-team` 已落地完整 RBAC，用户可登录、看到团队、查看权限树。但**所有团队都是空团队**——没有绑定任何集群，无法部署任何应用。要打通"用户 → 团队 → 集群 → 应用"主线，下一步就是**集群管理**：管理员添加集群（写 `region_info` 表）、团队开通集群（写 `tenant_region` 表）、查询集群信息（feature / publickey / namespaces / tenants）。

`migrate-console-region-client` 已落地 region API 客户端基础设施（mTLS、`RegionInfoRepository` 只读读出 region 配置、`RegionClientFactory` 装配 RestClient），但**只读**：本 change 要把 `region_info` 表的写入路径打通（添加/删除集群），并让上层 controller 通过 `RegionClientFactory` 真正调 Rainbond Go 集群拿 publickey/feature/namespaces 等数据。

## What Changes

### 集群（region）生命周期管理

- ADDED：`EnterpriseRegionsController`（路径 `/console/enterprise/{enterprise_id}/regions`）
  - `GET` —— 列出 enterprise 内所有 region；支持 `?status=` 与 `?check_status=` 过滤
  - `POST` —— 添加新集群：解析 token（kubectl-format YAML，含 url + ca + cert + key + region_id）→ 落 `region_info` 行
  - `GET /{region_id}` —— 集群详情
  - `PUT /{region_id}` —— 修改集群（alias/desc/url/cert）
  - `DELETE /{region_id}` —— 删除集群（先校验无 team 在用 → 删 region_info 行 + evict client cache）
- ADDED：`RegionInfo` entity（写入版） + `RegionInfoEntityRepository`（JpaRepository，对应已有的 `RegionInfoRepository` JdbcTemplate 只读保留作 client 装配用）

### 集群授权（License）

- ADDED：`RegionLicenseController`（路径 `/console/enterprise/{enterprise_id}/regions/{region_name}/license`）
  - `GET /enterprise/{enterprise_id}/licenses` —— enterprise 维度的 license 列表
  - `GET /cluster-id` —— 拿当前集群的 cluster-id（用于线下生成 license）
  - `POST /activate` —— 提交 license body 激活集群
  - `GET /status` —— 当前集群授权状态（剩余资源/到期时间）

### 团队-集群关联（team-region）

- ADDED：`TeamRegionController`（路径 `/console/teams/{team_name}/region`）
  - `GET /query` —— 当前 team 已开通的集群
  - `GET /unopen` —— 当前 team 未开通的集群（与已开通取差集）
  - `POST` —— 开通集群（写 `tenant_region` 行 + 调 region API `createTenant` 在集群侧建 namespace）

### Region 元信息查询

- ADDED：`RegionQueryController`
  - `GET /console/regions` —— 列出所有 region（旧 PaaS 全局视图，简版）
  - `GET /console/teams/{team_name}/regions/{region_name}/publickey` —— 集群公钥（部署时签名用）
  - `GET /console/teams/{team_name}/regions/{region_name}/features` —— 集群 feature flag
  - `GET /console/teams/{tenant_name}/protocols` —— 协议列表（HTTP/TCP/gRPC 等）

### 命名空间与资源（read-only）

- ADDED：`ClusterNamespacesController`
  - `GET /console/teams/cluster/namespaces` —— 当前集群已存在的所有 namespace（用于"绑定已有 namespace"场景）
  - `GET /console/enterprise/{enterprise_id}/regions/{region_id}/namespace` —— enterprise 维度的 namespace 列表
  - `GET /console/enterprise/{enterprise_id}/regions/{region_id}/resource` —— region 资源汇总
- ADDED：`EnterpriseRegionTenantsController`
  - `GET /console/enterprise/{enterprise_id}/regions/{region_id}/tenants` —— 集群里所有 tenant 的资源占用
  - `POST /console/enterprise/{enterprise_id}/regions/{region_id}/tenants/{tenant_name}/limit` —— 设置 tenant 在集群上的资源上限

### 镜像仓库凭据

- ADDED：`HubRegistryController`（路径 `/console/hub/registry`）—— 平台级镜像仓库凭据（admin 用）
  - `GET /` —— 列表
  - `POST /` —— 新增
  - `PUT /` —— 修改
  - `DELETE /?secret_id={id}` —— 删除
  - `GET /image` —— 列出仓库镜像（调 registry HTTP API）
- ADDED：`TeamRegistryAuthController`（路径 `/console/teams/{team_name}/registry/auth`）
  - `GET / POST` —— 团队级镜像仓库凭据列表/新增
  - `GET /{secret_id}` `PUT /{secret_id}` `DELETE /{secret_id}` —— 单个凭据 RUD

### Region API 调用扩展

- MODIFIED：`ClusterOperations`（已存在的 14 接口骨架之一）—— 实现 `getRegionFeatures` / `getRegionPublicKey`（已部分在 TenantOperations）/ `getRegionNamespaces` / `getRegionResources` / `getClusterId` / `activateLicense` / `getLicenseStatus`
- ADDED：`HelmOperations` 部分实现 `listImages` 用于 registry image listing

### 不进入此 change（明确 punt）

- **RKE2 一键部署集群**（`console/views/rke2.py` 467 LOC + k8s 直连）—— 单独 change `migrate-console-rke2`，需引入 `kubernetes-client-java` 业务实现 + SSE 日志流
- **region monitor / dashboard / lang_version / mavensettings / cnb / batch-gateway** —— 留给 `migrate-console-misc` 或对应业务 change（如 `migrate-console-app-create` 处理 lang_version / cnb / mavensettings）
- **platform-plugins / abilities** —— `migrate-console-plugin`
- **kubeblocks** —— 独立 change `migrate-console-kubeblocks`
- **topological / query_range / monitor** —— `migrate-console-app-runtime`
- **convert-resource** —— `migrate-console-misc`

## Capabilities

### New Capabilities

无（不引入新 capability）

### Modified Capabilities

- `kuship-console-app`：在已有 35 requirements 基础上 ADDED 集群生命周期 / License / 团队-集群关联 / Region 元信息 / 命名空间 / Registry 等 requirements；MODIFIED `region_info 表只读访问`（升级为 read+write 完整 JPA entity）+ `Region API 客户端基础设施`（`RegionClientFactory.evict` 在删除集群时强制调用）

## Impact

- **新增 entity**：`RegionInfo`（升级原 `RegionInfoDto` 为完整 `@Entity`）+ 写路径；`HubRegistryAuth`（新表 `console_hub_image_repository`）+ `TeamRegistryAuth`（新表 `tenant_registry_auth`）
- **共享 schema**：`region_info`（已存在）+ 新增 `console_hub_image_repository` 与 `tenant_registry_auth` 两张表也由 rainbond-console 拥有 schema 演进权（rainbond Django migrations 已建好这两表）
- **token 解析**：rainbond `add_region` 接受 kubectl-format YAML（含 base64 cert/key），需要在 `RegionService.parseToken` 复刻该解析逻辑（YAML → cluster fields）
- **Region client cache evict**：删除集群时 SHALL 显式调 `RegionClientFactory.evict(enterpriseId, regionName)`，否则缓存里的旧 RestClient 仍可用于已删 region
- **kuship-ui**：`services/region.js` 完整解锁；前端可在"集群管理"面板增删改查集群、团队设置页可开通/关闭集群、应用部署界面可看到集群 feature flag 与 namespace 列表
