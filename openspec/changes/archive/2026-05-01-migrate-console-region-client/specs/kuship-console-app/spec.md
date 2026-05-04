## ADDED Requirements

### Requirement: Region API 客户端基础设施

kuship-console SHALL 提供 `RegionClientFactory`（@Component）作为所有 region API 调用的统一入口；该 factory SHALL 按 `(enterprise_id, region_name)` 缓存 Spring 6 `RestClient` 实例，首次访问时从 `region_info` 表加载 mTLS 证书并装配 client；连接池采用 `PoolingHttpClientConnectionManager`，每路由最大连接数等于 `cpu_count() * 5`（与 rainbond-console Python 端 `multiprocessing.cpu_count() * 5` 一致），connect/socket 超时默认 5 秒。

#### Scenario: 首次访问触发懒加载

- **WHEN** 业务 service 首次调用 `regionClientFactory.getClient("region-1", "ent-uuid")`
- **THEN** factory 查询 `region_info` 表对应行，加载 `ssl_ca_cert`/`cert_file`/`key_file`，构造 `RestClient` 并缓存
- **AND** 第二次访问同 `(region-1, ent-uuid)` 直接返回缓存中的同一个 `RestClient` 实例

#### Scenario: 多 region 各自独立缓存

- **WHEN** 同一进程访问 `region-a` 与 `region-b` 各一次
- **THEN** 缓存中存在两个独立的 `RestClient` 实例，互不干扰

#### Scenario: 主动失效缓存

- **WHEN** 调用方调 `regionClientFactory.evict("region-1", "ent-uuid")`
- **THEN** 缓存中对应条目被移除，下次访问该 region 触发重新加载

#### Scenario: region 不存在

- **WHEN** 调用 `regionClientFactory.getClient("not-exist", "ent-uuid")`
- **THEN** 抛 `RegionApiException`，`code=500`，`msg_show="集群配置不存在"`

### Requirement: mTLS 与多 region 客户端装配

kuship-console SHALL 支持「PEM 内联文本」与「文件路径」两种 mTLS 证书形式（与 rainbond-console Python 端 `Configuration` 类完全一致）：若 `region_info.ssl_ca_cert`/`cert_file`/`key_file` 字段值以 `/` 开头，视为文件路径直接读取；否则视为 PEM 内联文本，**在内存构造 `KeyStore`，不落盘**。HTTPS 校验由 `kuship.region.ssl-verify` 配置开关控制（默认 `false`，对应 Python `REGION_SSL_VERIFY=false`）。

#### Scenario: 内联 PEM 证书装配

- **WHEN** `region_info` 行的 `ssl_ca_cert` 是多行 PEM 文本（以 `-----BEGIN CERTIFICATE-----` 开头）
- **THEN** factory 在内存解析为 `X509Certificate`，构造 `KeyStore`（PKCS12），喂给 `SSLContext`，**不在文件系统创建任何 ssl 临时目录**

#### Scenario: 文件路径证书装配

- **WHEN** `region_info` 行的 `ssl_ca_cert` 值是绝对路径（如 `/etc/kuship/ca.pem`）
- **THEN** factory 直接从该路径读取证书文件加载

#### Scenario: ssl-verify 关闭

- **WHEN** `kuship.region.ssl-verify=false`（默认）
- **THEN** 装配的 `SSLContext` 使用 trust-all `TrustManager`，不验证服务端证书域名/链

#### Scenario: ssl-verify 开启

- **WHEN** `kuship.region.ssl-verify=true`（生产推荐）
- **THEN** 装配的 `SSLContext` 启用标准证书校验，使用 `region_info.ssl_ca_cert` 作为 trust store

### Requirement: Region API 错误映射

kuship-console SHALL 通过 `RegionApiResponseProcessor` 把 Go 后端响应映射为强类型 DTO 或对应异常；映射规则 100% 对齐 rainbond-console `regionapibaseclient.py:_check_status` 的行为；HTTP 状态码与业务 `code` 解耦：异常对象内部保留 `httpStatus` 仅供调试，对外响应体的 `code` 字段使用 region 响应 body 的 `code` 字段。

#### Scenario: 标准成功响应

- **WHEN** Go 后端返回 HTTP 200 + body `{"code":200,"msg":"success","msg_show":"OK","data":{"bean":{...}}}`
- **THEN** `RegionApiResponseProcessor` 反序列化 `data.bean` 为指定 DTO 类型并返回

#### Scenario: HTTP 200 + 空 body

- **WHEN** Go 后端返回 HTTP 200 但 body 为空或非合法 JSON
- **THEN** 抛 `RegionApiException`，`msg="request region api body is nil"`，`msg_show="集群请求网络异常"`

#### Scenario: 4xx-5xx + body 含 code

- **WHEN** Go 后端返回 HTTP 400/500 + body `{"code":404,"msg":"team not found","msg_show":"团队不存在"}`
- **THEN** 抛 `RegionApiException`，`code=404`，`msg="team not found"`，`msg_show="团队不存在"`

#### Scenario: HTTP 401 + bean.code=10400 → InvalidLicense

- **WHEN** Go 后端返回 HTTP 401 + body `{"data":{"bean":{"code":10400,"msg":"license expired"}}}`
- **THEN** 抛 `InvalidLicenseException`

#### Scenario: HTTP 409 + 频繁操作短语 → RegionApiFrequent

- **WHEN** Go 后端返回 HTTP 409 + body.msg 等于 `"操作过于频繁，请稍后再试"` 或 `"wait a moment please"` 或 `"just wait a moment"`（来自 `kuship.region.frequent-operation-messages` 配置，不区分大小写）
- **THEN** 抛 `RegionApiFrequentException`

#### Scenario: HTTP 409 + 非频繁操作短语 → RegionApiException

- **WHEN** Go 后端返回 HTTP 409 + body.msg 是其他业务消息
- **THEN** 抛 `RegionApiException`，`code=409`，`msg/msgShow` 透传 body

#### Scenario: HTTP 412 + 字面错误码

- **WHEN** Go 后端返回 HTTP 412 + body.msg 等于 `"cluster_lack_of_memory"`
- **THEN** 抛 `ClusterLackOfMemoryException`
- **AND** 同样匹配 `tenant_lack_of_memory`/`tenant_lack_of_cpu`/`tenant_quota_cpu_lack`/`tenant_quota_memory_lack`/`authorize_cluster_lack_of_memory`/`authorize_cluster_lack_of_node`/`authorize_cluster_lack_of_license`/`authorize_expiration_of_authorization` 共 9 种字面错误码，分别抛对应专门异常类

#### Scenario: socket 错误重试一次

- **WHEN** RestClient 调用过程中抛出 socket 类异常（IOException/SocketException/SocketTimeoutException）
- **THEN** factory 重试一次；若再次失败则抛 `RegionApiSocketException`

### Requirement: Region API 错误消息中文化

kuship-console SHALL 提供 `RegionErrorMsgEnricher` 把 Go 后端原始英文错误消息映射为用户友好的中文 `msg_show`；至少覆盖 rainbond-console Python 端 `build_region_error_msg_show` 当前已实现的三种模式：Helm 接管冲突、域名冲突、频繁操作短语。其他模式 `msg_show` 默认等于原始 `msg`。

#### Scenario: Helm 接管冲突中文化

- **WHEN** Go 后端返回错误 body.msg 含 `"...exists and cannot be imported into the current release: invalid ownership metadata;... meta.helm.sh/release-name\": must be set to \"my-release\";... meta.helm.sh/release-namespace\": must be set to \"my-ns\""`
- **THEN** 经 enricher 处理后 `msg_show` 含 `"命名空间 my-ns 中已存在资源 ...，且缺少 Helm 接管元数据，Release my-release 无法继续安装"`

#### Scenario: Helm 接管冲突无具体捕获组

- **WHEN** body.msg 含关键词 `"cannot be imported into the current release"` + `"invalid ownership metadata"` 但正则不匹配捕获组
- **THEN** `msg_show` 是兜底文案 `"命名空间中已存在同名资源，且缺少 Helm 接管元数据，请先删除冲突资源或补齐 Helm 元数据后重试"`

#### Scenario: 域名冲突中文化

- **WHEN** body.msg 形如 `"domain conflict: domain 'a.com' conflicts with existing domain 'b.com' in namespace 'ns' (resource: ingress/foo)"`
- **THEN** `msg_show` 包含中文域名冲突说明，含原始 domain/namespace/resource

#### Scenario: 其他消息原样透传

- **WHEN** body.msg 是任何不匹配上述两种模式的英文消息
- **THEN** `msg_show` 等于原始 `msg` 字符串（与 Python `build_region_error_msg_show` 默认行为一致）

### Requirement: 14 个资源域接口骨架

kuship-console SHALL 在 `cn.kuship.console.infrastructure.region.api` 包下提供 14 个资源域接口（`TenantOperations`、`ServiceOperations`、`ServiceDependencyOperations`、`ServiceEnvOperations`、`ServicePortOperations`、`ServiceVolumeOperations`、`ServiceProbeOperations`、`ServiceLifecycleOperations`、`ServiceStatusOperations`、`ServiceLogOperations`、`EventOperations`、`HelmOperations`、`GatewayOperations`、`ClusterOperations`），每个接口声明该资源域的全部 method 签名（约 25 个）+ JavaDoc 标注预期实现 change 名；每个未实现的 method 实现类方法体 SHALL 仅 `throw new UnsupportedOperationException("not yet implemented; will be filled in by migrate-console-* change")`。

#### Scenario: 接口存在且签名稳定

- **WHEN** 检查 `cn.kuship.console.infrastructure.region.api` 包
- **THEN** 14 个接口均存在；每个接口的 method 签名包括 `regionName` / `tenantName` 等 path/query 参数与对应的请求/响应类型

#### Scenario: 未实现 method 抛 UnsupportedOperationException

- **WHEN** 调用 `serviceOperations.createService("region-1", "tenant-x", body)`（本 change 不实现）
- **THEN** 抛 `UnsupportedOperationException`，message 包含 `"not yet implemented"` 与对应 change 名（`"migrate-console-app-create"`）

#### Scenario: JavaDoc 标注预期实现 change

- **WHEN** 阅读 `ServiceLifecycleOperations.startService` 等未实现 method 的 JavaDoc
- **THEN** 文档明确指出该 method 由哪个后续 change（如 `migrate-console-app-runtime`）落地

### Requirement: TenantOperations 完整能力（示范）

kuship-console SHALL 完整实现 `TenantOperations` 接口的 5 个 method 作为基础设施可用性示范：`createTenant`、`deleteTenant`、`getTenantResources`、`getRegionPublickey`、`getRegionLabels`；每个 method 配套强类型 DTO（`CreateTenantReq` / `TenantResourcesResp` / `RegionPublickeyResp` / `RegionLabelsResp`）；URL 路径与 HTTP method 严格对齐 rainbond-console `regionapi.py` 中对应 method 的实现。

#### Scenario: createTenant POST 调用

- **WHEN** 调用 `tenantOperations.createTenant("region-1", new CreateTenantReq("name","id","ent-id","ns",false))`
- **THEN** 向 `/v2/tenants` 发起 POST，body 含 tenant 名/id/enterpriseId/namespace/bind_existing
- **AND** 解析 `data.bean` 为 `TenantResourcesResp`（或对应类型）返回

#### Scenario: deleteTenant DELETE 调用

- **WHEN** 调用 `tenantOperations.deleteTenant("region-1", "tenant-x")`
- **THEN** 向 `/v2/tenants/tenant-x` 发起 DELETE，无 body

#### Scenario: getTenantResources GET 调用

- **WHEN** 调用 `tenantOperations.getTenantResources("region-1", "tenant-x", "ent-id")`
- **THEN** 向 `/v2/tenants/tenant-x/res?enterprise_id=ent-id` 发起 GET，反序列化为 `TenantResourcesResp`

#### Scenario: 4xx 错误自动映射

- **WHEN** Go 后端对 `createTenant` 返回 HTTP 409 + body `{"code":409,"msg":"tenant already exists","msg_show":"团队已存在"}`
- **THEN** 抛 `RegionApiException(code=409, msg="tenant already exists", msgShow="团队已存在")`
- **AND** 由 `GlobalExceptionHandler` 映射为对外响应体 `{"code":409,"msg":"tenant already exists","msg_show":"团队已存在","data":{"bean":{},"list":[]}}`

### Requirement: region_info 表只读访问

kuship-console SHALL 通过 `RegionInfoRepository`（基于 `JdbcTemplate`）以**只读**方式访问 `region_info` 表，**不引入 JPA `@Entity`**，**不写入任何字段**；表的 schema 演进权属于 rainbond-console（Django migrations）。

#### Scenario: 按 region_name 查询

- **WHEN** 调用 `regionInfoRepository.findByName("region-1")`
- **THEN** 返回 `Optional<RegionInfoDto>` 含 `regionId/regionName/url/wsurl/sslCaCert/certFile/keyFile/enterpriseId` 等字段（snake_case 列名映射到 camelCase Java 字段）

#### Scenario: 不存在返回空

- **WHEN** 调用 `regionInfoRepository.findByName("not-exist")`
- **THEN** 返回 `Optional.empty()`

#### Scenario: 不引入 entity 类

- **WHEN** 检查 `cn.kuship.console.infrastructure.region` 包
- **THEN** 不存在任何 `@Entity` 注解的类；`RegionInfoDto` 是普通 record / POJO

## MODIFIED Requirements

### Requirement: 全局异常映射

kuship-console SHALL 通过 `@RestControllerAdvice` 把以下异常类型统一映射为 `general_message` 形状的响应；HTTP 状态码与业务 `code` 解耦：除认证（401）与授权（403）由 Spring Security 自身的 EntryPoint / Handler 写出对应 HTTP 状态码外，其他异常 SHALL 一律返回 HTTP 200，业务 `code` 走响应体 `code` 字段。Region 异常族（`RegionApiException`、`RegionApiFrequentException`、`InvalidLicenseException`、`ClusterLackOfMemoryException`、`TenantLackOfMemoryException`、`TenantLackOfCpuException`、`TenantQuotaCpuLackException`、`TenantQuotaMemoryLackException`、`ClusterAuthLackOfMemoryException`、`ClusterAuthLackOfNodeException`、`ClusterAuthLackOfLicenseException`、`ClusterAuthLackOfLicenseExpireException`、`RegionApiSocketException`）也 SHALL 被映射为对应业务 code 的 general_message 响应。

#### Scenario: ServiceHandleException 透传 code/msg/msgShow

- **WHEN** controller 抛出 `new ServiceHandleException(404, "team not found", "团队不存在")`
- **THEN** HTTP 状态码为 200
- **AND** 响应体形如 `{"code":404,"msg":"team not found","msg_show":"团队不存在","data":{"bean":{},"list":[]}}`

#### Scenario: 参数校验失败

- **WHEN** controller 接收的请求体或 query 参数触发 `MethodArgumentNotValidException` 或 `ConstraintViolationException`
- **THEN** 响应体 `code=400`、`msg_show="参数校验失败"`、`data.bean.errors` 包含字段级错误明细列表

#### Scenario: 反序列化失败

- **WHEN** 客户端发送的请求体 JSON 不合法（触发 `HttpMessageNotReadableException`）
- **THEN** 响应体 `code=400`、`msg_show="请求体解析失败"`

#### Scenario: 缺失 Header / 类型不匹配

- **WHEN** 触发 `MissingRequestHeaderException` 或 `MethodArgumentTypeMismatchException`
- **THEN** 响应体 `code=400`，`msg` 字段包含具体字段名

#### Scenario: 兜底 Exception

- **WHEN** controller 抛出未在专用 handler 中处理的异常
- **THEN** HTTP 状态码 200、响应体 `code=500`、`msg_show="系统异常"`、`data.bean.trace_id` 包含本次请求的 traceId
- **AND** 服务端 ERROR 级别日志输出完整堆栈与同一 traceId

#### Scenario: RegionApiException 透传业务码

- **WHEN** service 层调用 region API 抛出 `RegionApiException(code=409, msg="tenant already exists", msgShow="团队已存在")`
- **THEN** 响应体 `code=409`、`msg="tenant already exists"`、`msg_show="团队已存在"`，HTTP 200

#### Scenario: RegionApiFrequentException 映射为 429 业务码

- **WHEN** service 层抛出 `RegionApiFrequentException`
- **THEN** 响应体 `code=429`、`msg_show="操作过于频繁，请稍后再试"`，HTTP 200

#### Scenario: InvalidLicenseException 映射

- **WHEN** service 层抛出 `InvalidLicenseException`
- **THEN** 响应体 `code=10400`、`msg_show="集群授权失效或未授权"`，HTTP 200

#### Scenario: 资源不足异常族映射

- **WHEN** service 层抛出 `ClusterLackOfMemoryException` / `TenantLackOfMemoryException` / `TenantLackOfCpuException` 等
- **THEN** 响应体 `code` 为对应业务码（412 类），`msg` 为原始 region 错误码字面（`cluster_lack_of_memory` 等），`msg_show` 为对应中文文案（如 `"集群内存不足"`、`"团队内存配额不足"`）

#### Scenario: RegionApiSocketException 映射为 503

- **WHEN** service 层抛出 `RegionApiSocketException`（socket 重试后仍失败）
- **THEN** 响应体 `code=503`、`msg_show="集群网络不可达"`，HTTP 200
