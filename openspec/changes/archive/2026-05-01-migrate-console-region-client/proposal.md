## Why

后续所有业务 controller / service 都需要通过 HTTP 调用 Rainbond Go 集群（核心数据面），rainbond-console 一侧把 4416 行（含 base 566）+ 365 个 method 的客户端逻辑塞在 `www/apiclient/regionapi.py` 与 `regionapibaseclient.py` 中。如果不在第三步把客户端**基础设施层**（HTTP 工厂、mTLS、错误映射、错误消息中文化、连接池、超时、缓存）一次性钉死，每个业务 change 都会重复实现这些底层逻辑，且很难保证 4416 行代码里的细节（特殊错误码字面量、HTTP 200+空 body 处理、HTTP 状态码与业务 code 解耦、Helm/域名冲突中文化）在多次迁移中保持一致。

但 365 个 method 一次性全量迁移工作量过大、scope 不可控。本 change 采取「**先骨架再填空**」策略：基础设施 + 14 个资源域的接口签名（带 JavaDoc）一次到位；具体 method 实现仅完成 1 个完整资源域（TenantOperations，5 method）作为示范；其余 360+ method 留 `throw new UnsupportedOperationException(...)`，由后续业务 change 在自己的范围内"就地填空"。

## What Changes

- 新增 `cn.kuship.console.infrastructure.region.client.RegionClientFactory`（@Component）：按 `(enterprise_id, region_name)` 缓存 Spring 6 `RestClient` 实例；首次访问时从 `region_info` 表加载 mTLS 证书并装配 client
- 新增 mTLS 装配逻辑：支持「PEM 内联文本」与「文件路径」两种证书形式（与 Python 版完全一致），内联 PEM 在内存构造 `KeyStore`，**不再落盘到 `data/<enterprise_id>-<region_name>/ssl/`**（Python 老式做法的现代化）
- HTTP 客户端：Spring 6 `RestClient` + `HttpComponentsClientHttpRequestFactory`（HttpClient5），连接池 `PoolingHttpClientConnectionManager`，maxPerRoute=5*cpu（与 Python 一致），connect/socket 超时 5s
- 新增 `RegionApiResponse<T>` + `RegionApiResponseProcessor`：统一解析 Go 后端 `{code,msg,msg_show,data:{bean,list}}` 形状响应为强类型 DTO
- 新增 region 异常族（11 个异常类）：`RegionApiException`、`RegionApiFrequentException`、`RegionApiSocketException`、`InvalidLicenseException`、`ClusterLackOfMemoryException`、`TenantLackOfMemoryException`、`TenantLackOfCpuException`、`TenantQuotaCpuLackException`、`TenantQuotaMemoryLackException`、`ClusterAuthLackOfMemoryException`、`ClusterAuthLackOfNodeException`、`ClusterAuthLackOfLicenseException`、`ClusterAuthLackOfLicenseExpireException`
- 新增错误码识别逻辑：HTTP 412 + body.msg 字面匹配（`cluster_lack_of_memory` / `tenant_lack_of_memory` / `tenant_lack_of_cpu` / `tenant_quota_cpu_lack` / `tenant_quota_memory_lack` / `authorize_cluster_lack_of_memory` / `authorize_cluster_lack_of_node` / `authorize_cluster_lack_of_license` / `authorize_expiration_of_authorization`）；HTTP 401 + `bean.code=10400` → InvalidLicense；HTTP 409 + 非"频繁操作"短语 → ServiceHandleException；HTTP 4xx-5xx + 含 `code` → RegionApiException；HTTP 200+空 body → RegionApiException("集群请求网络异常")
- 新增 `RegionErrorMsgEnricher`：移植 Python `build_region_error_msg_show`、覆盖 Helm 接管冲突 / 域名冲突 / 频繁操作短语三种模式
- 新增 14 个资源域接口（仅签名 + JavaDoc）：`TenantOperations`、`ServiceOperations`、`ServiceDependencyOperations`、`ServiceEnvOperations`、`ServicePortOperations`、`ServiceVolumeOperations`、`ServiceProbeOperations`、`ServiceLifecycleOperations`、`ServiceStatusOperations`、`ServiceLogOperations`、`EventOperations`、`HelmOperations`、`GatewayOperations`、`ClusterOperations`；每接口约 25 method，每个 method 实现仅 `throw new UnsupportedOperationException("not yet implemented; will be filled in by migrate-console-* change")`
- **示范实现**：`TenantOperations` 5 个 method 完整落地（createTenant / deleteTenant / getTenantResources / getRegionPublickey / getRegionLabels），含强类型 DTO（`CreateTenantReq` / `TenantResourcesResp` / `RegionPublickeyResp` / `RegionLabelsResp`）
- 新增 `RegionInfoRepository`：用 `JdbcTemplate` 直读 `region_info` 表（不引入 entity，schema 演进权在 Django 侧）
- 新增配置项：`kuship.region.timeout-seconds: 5`、`kuship.region.ssl-verify: false`、`kuship.region.connection-pool-max-per-route: 0`、`kuship.region.frequent-operation-messages: [...]`
- **修改** `migrate-console-response-contract` 已落地的 `GlobalExceptionHandler`：追加 region 异常族的映射，让所有 region 调用错误自动转成 general_message 形状响应（`RegionApiException.code` 透传到响应 `code`，`msgShow` 透传到 `msg_show`）
- 测试：`RegionClientFactoryTest`（mTLS 两种证书形式 + 缓存命中）、`RegionApiResponseProcessorTest`（标准成功 + 6 类错误响应映射）、`RegionErrorMsgEnricherTest`（3 种汉化模式）、`TenantOperationsIntegrationTest`（用 `MockRestServiceServer` 给 5 个 tenant method 各一个用例）
- 文档：`kuship-console/CLAUDE.md` 增加 "Region API client" 段落

**明确不进入此 change**：
- 360+ 未实现 method 的具体实现（留给各业务 change 按需填空）
- WebSocket（`wsurl` 字段）—— 留给 `migrate-console-app-runtime`
- 异步/响应式（不引入 WebClient）
- spring-retry / resilience4j（仅手写 1 次 socket 重试）
- region_info 表的写入（添加/删除集群）—— 留给 `migrate-console-region-cluster`
- enterprise_id 反查（service 层显式传入或后续从 RequestContext 取）

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `kuship-console-app`：新增 6 项契约（Region API 客户端基础设施、mTLS 与多 region 客户端缓存、Region API 错误映射、Region API 错误消息中文化、TenantOperations 完整能力、未实现 method 的占位行为），并修改 1 项已存在的 "全局异常映射" 以追加 region 异常族的映射规则。

## Impact

- **代码新增**：`infrastructure/region/{client, exception, response, errormsg, dto}` 共 ~30 个 Java 文件 + 14 个资源域接口 + `TenantOperationsImpl` + DTO + 测试
- **代码修改**：`common/exception/GlobalExceptionHandler` 追加 region 异常族 mapping
- **配置新增**：`application.yaml` 增加 `kuship.region.*` 段
- **数据库**：仅**只读**访问 `region_info` 表；不写入、不变更 schema
- **依赖**：HttpClient5 已在 init change 引入（标记 `<optional>true</optional>`，本 change 改成默认依赖）
- **API 契约**：本 change 不暴露任何新对外 endpoint
- **后续 change 解锁**：本 change 落地后，`migrate-console-region-cluster`、`migrate-console-application-core`、`migrate-console-app-create`、`migrate-console-app-runtime`、`migrate-console-app-market`、`migrate-console-plugin` 等所有业务 change 都可以注入对应资源域接口并就地填空 method 实现
- **运维影响**：生产部署时建议 `kuship.region.ssl-verify=true`（dev 默认 false 与 Python 端一致）
