## Context

- 截至本 change，kuship-console 已实现：响应/异常/JWT/分页 契约层、Region API 客户端基础设施（mTLS + 14 域接口骨架，仅 `TenantOperations` 完整落地）、账户/团队/权限全套（用户登录、team CRUD、@RequirePerm AOP）。
- `region_info` 表当前由 `infrastructure/region/repository/RegionInfoRepository`（JdbcTemplate）**只读**访问 —— 用于 `RegionClientFactory` 装配 mTLS RestClient。
- 集群管理是连接"账户体系"与"应用体系"的桥梁：管理员添加集群、团队开通集群后，应用部署 / 监控 / 日志才有运行环境。
- rainbond-console 端的关键约束：
  - 添加集群通过 **kubectl-format YAML token**（含 `ca.pem` / `client.pem` / `client.key.pem` / `apiAddress` / `websocketAddress` / `defaultDomainSuffix` / `defaultTCPHost`）一次性解析后落表；不暴露逐字段表单
  - License 4 个端点（cluster-id / activate / status / list）—— activate / status 实际转发给 region API（Go 后端验证授权）
  - rainbond 实际表名是 `team_registry_auths`（注意末尾 `s`）—— 与之前我推测的 `tenant_registry_auth` 不一致，需以真实 schema 为准

## Goals / Non-Goals

**Goals:**

1. 完成"添加集群 → 团队开通 → 部署应用"主线中"添加+开通"两环
2. `region_info` 表 read+write 完整生命周期，删除集群时强制 evict region client cache
3. License / Registry / Region 元信息（feature/publickey/namespace）等下游应用部署所需的元数据查询
4. 实现 `ClusterOperations` 的 6 个核心 method（getRegionFeatures / getNamespaces / getResources / getClusterId / activateLicense / getLicenseStatus），让其他业务 change 直接复用

**Non-Goals:**

1. RKE2 一键部署集群（rke2.py 467 LOC + k8s 直连 + SSE 日志，独立 change）
2. region monitor / dashboard 反向代理（监控属于 app-runtime change）
3. lang_version / mavensettings / cnb（属于 app-create change）
4. platform-plugins / abilities（plugin change）
5. kubeblocks（独立 change）
6. topological / batch-gateway / convert-resource（misc / app-runtime）

## Decisions

### 决策 1：RegionInfo entity 与现有 RegionInfoDto 关系

**问题**：`infrastructure/region/repository/RegionInfoRepository`（JdbcTemplate 只读）已被 `RegionClientFactory` 使用，输出 `RegionInfoDto` record。本 change 引入 JPA `@Entity RegionInfo` 后，是否要废弃旧 DTO？

**选择**：
- 保留 `RegionInfoDto` + JdbcTemplate-based `RegionInfoRepository`（infrastructure 层，给 `RegionClientFactory` 用，不引入 hibernate session 副作用）
- 新增 `cn.kuship.console.modules.region.entity.RegionInfo` + `RegionInfoEntityRepository`（JPA，给业务 controller / service 用，支持 CRUD）
- 两者读同一张表 —— Hibernate 与 JdbcTemplate 共用 HikariCP；Hibernate 写入后 JdbcTemplate 立刻可见（同一个 connection pool 视图）
- 在删除集群的 service 方法里：先 JPA delete → 再 `RegionClientFactory.evict()` 显式失效缓存
- 其他业务 change 引用 region 时优先注入 `RegionInfoEntityRepository`（除非需要 `RegionClientFactory` 装配 mTLS client）

**Rationale**：
- 不破坏 region client 现有 16 测试用例（已在 `migrate-console-region-client` 通过）
- `RegionInfoDto` 与 mTLS factory 是底层 infrastructure，不应被 hibernate session 污染（避免 lazy-loading 在 region client 调用时抛异常）

**Alternatives considered**：
- ❌ 只用 JPA entity，废弃 JdbcTemplate Repository → 需重写 RegionClientFactory 的所有路径，引入回归风险
- ❌ 只用 JdbcTemplate，业务层手写 CRUD SQL → 不一致，项目已统一 JPA 路线

### 决策 2：token YAML 解析

**问题**：rainbond `parse_token` 接受 kubectl-format YAML（实际是 BaseLoader 解析的 dict，含 `ca.pem` / `client.pem` / `client.key.pem` 等 key），需要在 Java 端等价实现。

**选择**：
- 复用 Spring Boot 已带的 `org.yaml.snakeyaml.Yaml`（snakeyaml 是 Spring Boot 间接依赖，无需新增）
- 在 `RegionService.parseToken(String yaml)` 中调 `new Yaml().load(yaml)` 拿 Map，按 rainbond 错误顺序逐字段校验，缺失字段抛 `ServiceHandleException(400, "ca.pem not found", "CA 证书不存在")` 等，错误文案对齐 rainbond
- 输出 `RegionInfo` partial（含 url/wsurl/sslCaCert/certFile/keyFile/httpdomain/tcpdomain），其他字段（region_id / region_name / region_alias / region_type / status / enterprise_id）由 controller 层补充

**Rationale**：snakeyaml 是 BaseLoader 的最近 Java 等价；保留 rainbond YAML 字段命名（含 `.` 比如 `ca.pem`，Map.get 直接读）

### 决策 3：team_registry_auths 表名拼写差异

**问题**：rainbond Django 模型类名为 `TeamRegistryAuth`（单数），但实际数据库表名是 `team_registry_auths`（复数），与 rainbond 其他表（如 `tenant_info`、`role_info`）的命名约定不一致 —— Django 默认会把 model name 转为 snake_case 单数，复数 `s` 是手工加的。

**选择**：
- Java entity 类名 `TeamRegistryAuth`（单数，符合 JPA 命名习惯）
- `@Table(name = "team_registry_auths")`（显式指定 rainbond 实际表名）
- 文档化此异常，避免后续业务 change 误以为是 `team_registry_auth`

**Rationale**：rainbond schema 演进权属于 Django；本 change 严格 mirror，不重命名

### 决策 4：Hub Registry vs Team Registry 共享同一张表

**意外发现**（实地考察 rainbond 源码后修正）：rainbond 的 `HubRegistryView` **直接复用 `team_registry_auths` 表**，通过 `tenant_id=''` + `region_name=''` 区分平台级（hub）；team 级则填具体 tenant_id + region_name。

```
team_registry_auths 表
├── tenant_id='' + region_name=''  → 平台级（hub），通过 /console/hub/registry 端点
└── tenant_id=<id> + region_name=<r>  → 团队级，通过 /console/teams/{team_name}/registry/auth
```

**实现**：
- 单一 entity `TeamRegistryAuth`（对应 `team_registry_auths` 表）+ 单一 `TeamRegistryAuthRepository`
- 分两个 controller：`HubRegistryController`（`/console/hub/registry`）+ `TeamRegistryAuthController`（`/console/teams/{team_name}/registry/auth`）
- 共享 service `RegistryAuthService.create / update / delete / list`，参数化 tenant_id + region_name
- HubRegistryController 写入时强制 tenant_id='' + region_name=''；列表 filter 同样

**Rationale**：保持与 rainbond schema 完全一致；避免引入不存在的 `console_hub_image_repository` 表。

### 决策 5：team_region 与 PermRelTenant 关系

`migrate-console-account-team` 已落地 `TenantRegionInfo` entity（对应 `tenant_region` 表）。本 change 不再新增 entity，仅在 `TeamRegionService` 里通过已有 repository 调用：

- `GET /teams/{team_name}/region/query` → `tenantRegionRepo.findByTenantId(team.getTenantId())`
- `GET /teams/{team_name}/region/unopen` → enterprise 内全集 - 已开通的差集
- `POST /teams/{team_name}/region` → 写 `tenant_region` 行 + 调 `TenantOperations.createTenant(regionName, enterpriseId, ...)` 在集群侧建 namespace

### 决策 6：License 端点的转发策略

License 4 个端点本质是 region API 的 pass-through：

- `GET /enterprise/{eid}/regions/{rname}/license/cluster-id` → 调 region `GET /v2/cluster/cluster-id`
- `POST /.../license/activate` → 调 region `POST /v2/cluster/license-activate`
- `GET /.../license/status` → 调 region `GET /v2/cluster/license-status`
- `GET /enterprise/{eid}/licenses` → 不调 region，是 enterprise 维度的 license summary（聚合 N 个 region 的 status）

**实现**：在 `ClusterOperations` 接口加 4 个 method，`ClusterOperationsImpl` 实现转发逻辑（沿用 `TenantOperations` 的 RestClient 模式）。本 change 在 ClusterOperations 上的工作量是首个 6 method 完整实现，证明 14 接口骨架可用。

### 决策 7：HubRegistry 的 image 列表怎么调

`GET /console/hub/registry/image` 是从 registry HTTP API 拿镜像列表（如 Docker Registry V2 API 的 `/v2/_catalog`）。这不是调 Rainbond Go 集群，而是调用户配置的 registry 端点。

**实现**：单独的 `RegistryHttpClient`（用 RestClient + Basic Auth），不走 `RegionClientFactory`。失败重试 1 次，超时 5s。

### 决策 8：本 change 是否扩展 RegionInfoRepository（infrastructure 层）

不扩展。infrastructure 层的 `RegionInfoRepository`（JdbcTemplate）保持只读 + read-only DTO 输出；写入只走业务层的 `RegionInfoEntityRepository`。

`RegionClientFactory.evict(enterpriseId, regionName)` 已有 method 不变，本 change 在 service 删除 region 时显式调用。

## Risks / Trade-offs

- **[Region 删除影响应用]** 删 region 会让该 region 上的所有 team / app / service 失去运行环境。
  - **Mitigation**：删除 service 中加预校验：`tenant_region` 表中若有该 region 的关联 → 拒绝删除（`code=400`、`msg_show="该集群仍有团队在使用，请先关闭团队的集群关联"`）；rainbond 实际行为也是这样
- **[token 解析与原版 rainbond 偏差]** snakeyaml BaseLoader 与 Python yaml.BaseLoader 在边缘格式（多文档、anchors）上行为不同。
  - **Mitigation**：仅支持单文档 YAML（rainbond 实际生成的 token 格式都是单文档）；在 `RegionServiceTest` 中用 rainbond 实际 token fixture 验证
- **[License 端点的安全]** activate body 含 license 字符串，落 region 时若 token 泄露会被 region 端验证。
  - **Mitigation**：activate body 不落 console 库；仅转发到 region；`@RequireEnterpriseAdmin` 注解保护
- **[Hub Registry 凭据明文存储]** rainbond schema 中 password 是明文（varchar(255)）。
  - **Mitigation**：本 change 不引入加密（与 rainbond 一致）；记入 hardening backlog（独立 `migrate-console-secrets-encryption` change 用 Jasypt 处理 password / token / cert/key 等）
- **[hub 镜像列表调用 registry HTTP API]** 不可控的第三方 registry 可能返回慢或挂。
  - **Mitigation**：5s 超时 + 1 次重试；调用失败 fallback 到空列表 + warning 日志

## Migration Plan

1. PR 1：`RegionInfo` entity + `RegionInfoEntityRepository` + `EnterpriseRegionsController`（GET 列表 + GET 详情，read-only 入门）
2. PR 2：`RegionService.parseToken` + `EnterpriseRegionsController` POST/PUT/DELETE
3. PR 3：`TeamRegionController`（GET query / unopen / POST 开通）+ team-region join 逻辑
4. PR 4：`ClusterOperations` 6 method 实现 + `RegionLicenseController` + `RegionQueryController`
5. PR 5：`HubRegistryController` + `TeamRegistryAuthController`（含两个 entity）
6. PR 6：集成测试（4 个测试类）+ 文档

无需 DB 迁移；本 change 不发任何 DDL。

## Open Questions

- **Region 字段 `provider` / `provider_cluster_id`** —— rainbond 用于云厂商对接（阿里云/腾讯云）。本 change 沿用透传，不做特殊处理。如果未来 kuship 要做云厂商集成，会另起 change
- **Region monitor 端点（`/console/regions/monitor`）** —— rainbond 实际是 prometheus 探针聚合；归到 `migrate-console-app-runtime` 跟其他监控端点一并做更合适
- **Region public key 用途** —— 部署时签名（rainbond 自己 service share 流程用），本 change 仅做透传，不验签
