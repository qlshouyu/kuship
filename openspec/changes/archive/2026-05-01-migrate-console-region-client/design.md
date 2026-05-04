## Context

`migrate-console-response-contract` 已落地全局响应/异常/JWT/上下文/分页/TraceId 契约层。但所有业务 controller 真正能干活之前，必须先把"调 Rainbond Go 集群"这一层基础设施盖好——否则每个业务 change 都会重写底层 HTTP 客户端、mTLS、错误映射，带来不一致与维护负担。

参考实现侧已确认事实（来自 `reference/rainbond-console`）：

- `www/apiclient/regionapi.py` 3850 行 + `regionapibaseclient.py` 566 行，**365 个 method**
- 类层级：`RegionApiBaseHttpClient`（基类，含 4 个内部异常）→ `RegionInvokeApi`
- mTLS：每个 region 三件套 `ssl_ca_cert` / `cert_file` / `key_file`，存 `region_info` 表 TEXT 字段，可以是 PEM 内联文本或文件路径
- Python 端按 `hash(url+ca+cert+key)` 缓存 `urllib3.HTTPSConnectionPool` 实例
- 连接池：`cpu_count() * 5` per pool
- 超时：`self.timeout = 5`
- HTTPS 校验：`os.environ.get("REGION_SSL_VERIFY", "false") == "true"`
- 错误映射（`_check_status` line 158-202）：
  - 4xx-5xx + body 含 `code` → `ServiceHandleException(msg, msg_show, status_code, error_code, bean)`
  - HTTP 409 + body.msg 不属于"频繁操作"短语 → `ServiceHandleException`
  - HTTP 409 + body.msg 属于"频繁操作"短语 → `CallApiFrequentError`
  - HTTP 401 + `bean.code == 10400` → `InvalidLicenseError`
  - HTTP 412 + body.msg 字面 `cluster_lack_of_memory` → `ErrClusterLackOfMemory`（共 9 个 412 字面映射）
  - HTTP 200 + body 为空 → `ServiceHandleException("集群请求网络异常")`
- 错误消息汉化（`build_region_error_msg_show`）：
  - Helm 接管冲突（meta.helm.sh 元数据缺失）→ 翻译为命名空间冲突中文提示
  - 域名冲突（domain conflict + conflicts with existing domain）→ 域名冲突中文提示
  - "频繁操作"短语去重（`操作过于频繁，请稍后再试` / `wait a moment please` / `just wait a moment`）

`region_info` 表 schema（`reference/rainbond-console/console/models/main.py:932`）：

```
region_id varchar(36) unique
region_name varchar(64) unique
region_alias varchar(64)
region_type varchar(64) nullable (json string)
url varchar(256)
wsurl varchar(256)
httpdomain varchar(256)
tcpdomain varchar(256)
token varchar(255) nullable
status varchar(2)
create_time datetime
desc varchar(200)
scope varchar(10) default 'private'
ssl_ca_cert TEXT nullable
cert_file TEXT nullable
key_file TEXT nullable
enterprise_id varchar(36) nullable
provider varchar(24) nullable
provider_cluster_id varchar(64) nullable
```

约束：

- schema 演进权属于 Django 侧（init change 锁定）；本 change 仅 `JdbcTemplate` 只读
- 共享 SECRET_KEY、共享 MySQL 已就绪；JWT 上下文由前一 change 提供
- 后续 14 个资源域 method 数量预估：每域约 25 method，总计约 350 method（Python 365 略缩，因部分内部辅助 method 不暴露）

## Goals / Non-Goals

**Goals：**

- 新增任意一个业务 service 调 region API 时，只需 `@Autowired SomeOperations` + 调用对应 method 即可，**无需关心 mTLS、错误映射、消息汉化、连接池**
- mTLS 装配支持「PEM 内联」与「文件路径」两种证书形式（与 Python 完全一致），无需改 region_info 数据
- 错误映射逻辑 100% 对齐 Python `_check_status`，包括 9 个 412 字面错误码、HTTP 200+空 body 的特殊处理
- 错误消息汉化覆盖 Python 已实现的 Helm/域名/频繁操作三种模式
- 14 个资源域接口骨架就位，每个 method 都有签名 + JavaDoc，让后续业务 change 「就地填空」
- TenantOperations 5 个 method 完整落地作为示范（含 DTO + 测试）

**Non-Goals：**

- 360+ 未实现 method 的具体实现 —— 留给各业务 change
- WebSocket（实时日志/event 流）—— 留给 `migrate-console-app-runtime`
- 异步 / 响应式（WebClient）—— 不引入
- 高级重试（spring-retry / resilience4j）—— 本 change 仅手写 1 次 socket 重试
- region_info 写操作（add/delete cluster）—— 留给 `migrate-console-region-cluster`
- 跨 region 健康检查 / 自动 failover —— 暂不需要

## Decisions

### 决策 1：HTTP 客户端 = Spring 6 RestClient + HttpClient5

**选择：** `RestClient.builder().requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient5)).build()`，每个 region 一个独立实例。

**理由：**
- `RestClient` 是 Spring 6 的现代同步 HTTP API（替代过时的 RestTemplate），fluent API 更易用
- `HttpComponentsClientHttpRequestFactory` 把 HttpClient5 装成 Spring HTTP factory，HttpClient5 提供成熟的 mTLS 支持（`SSLContextBuilder` + `KeyStore`）
- init change 已经把 `httpclient5` 作为 optional 依赖引入，本 change 改成默认依赖即可

**备选：**
- `WebClient`（被否决，理由：异步模型与 console 大部分同步业务流不匹配；调试堆栈更难）
- 直接用 OkHttp（被否决，理由：与 Spring 集成需自写 Adapter，且 mTLS 配置 OkHttp 不如 HttpClient5 直观）

### 决策 2：mTLS 证书 = 内存 KeyStore，不落盘

**选择：** PEM 内联文本 → 用 BouncyCastle 或 JDK `CertificateFactory.getInstance("X.509")` + `KeyFactory.getInstance("RSA/EC")` 解析 → 在内存构造 `KeyStore.getInstance("PKCS12")` → 喂给 `SSLContextBuilder.loadKeyMaterial(keyStore, password)` 与 `loadTrustMaterial(trustStore, null)`。

**理由：**
- Python 老式落盘（`data/<enterprise>-<region>/ssl/`）容易残留 + 部署时多容器实例需额外卷管理 + 证书轮换需要 reload 文件
- 内存 KeyStore 不需要任何磁盘 IO，证书内容直接来自 `region_info` TEXT 字段
- BouncyCastle 在 Java 21 + GraalVM 下友好（已有 native-image 支持配置）

**备选：**
- 落盘（被否决，理由：与现代化云原生部署（K8s + read-only fs）矛盾）
- 走 JKS（被否决，PKCS12 是 JDK 9+ 默认，更普及）

**风险与缓解：**
- 内存 KeyStore 在 GraalVM native 下可能有 reflect-config 需求 → 留到 Phase 13 enable-graalvm-native 处理
- 同一 region 多次创建 client 浪费 → 由决策 3 的缓存解决

### 决策 3：客户端缓存 key = `(enterprise_id, region_name)`

**选择：** `RegionClientFactory` 内部 `Map<RegionClientKey, RestClient>`（`ConcurrentHashMap`，`computeIfAbsent`），key = `(enterpriseId, regionName)` record。

**理由：**
- Python 端用 `hash(url + ca + cert + key)` 做缓存 key，但实质是 `(enterprise_id, region_name)` 的代理（同一 region 的 url+证书在 region_info 行内绑定）
- record key 比 hash 更稳定（证书更新时 region_name 不变，缓存自动失效需要外部触发）
- 添加 `RegionClientFactory.evict(enterpriseId, regionName)` 方法，让证书更新时由调用方主动失效缓存

**备选：**
- 弱引用 / Caffeine 自动过期（被否决：region 数量很少，不需要 LRU；过期会导致 mTLS 握手频繁）

### 决策 4：14 个资源域接口（按业务边界拆分）

**选择：** 不用单一巨型 `RegionApiClient` 接口，按资源域拆 14 个独立接口。

**接口 → 后续业务 change 映射：**

```
TenantOperations              ← 本 change 实现（示范）；migrate-console-account-team 补
ServiceOperations             ← migrate-console-app-create 实现
ServiceDependencyOperations   ← migrate-console-application-core 实现
ServiceEnvOperations          ← migrate-console-application-core
ServicePortOperations         ← migrate-console-application-core
ServiceVolumeOperations       ← migrate-console-application-core
ServiceProbeOperations        ← migrate-console-application-core
ServiceLifecycleOperations    ← migrate-console-app-runtime
ServiceStatusOperations       ← migrate-console-app-runtime
ServiceLogOperations          ← migrate-console-app-runtime（含 WS 后续 change）
EventOperations               ← migrate-console-app-runtime
HelmOperations                ← migrate-console-app-market
GatewayOperations             ← migrate-console-application-core（部分）+ region-cluster
ClusterOperations             ← migrate-console-region-cluster
```

**理由：**
- 业务 change 注入需要的接口集合即可，避免一次性引入 365 method 的巨型接口
- 每接口 ~25 method 单文件可读，且易做单接口 mock
- 14 个接口与 14 个业务 change 的语义对齐

**备选：**
- 单 facade（被否决：350+ method 单文件不可读）
- 按 HTTP path 拆（被否决：Python 端 method 命名以业务语义为主，不是 path）

### 决策 5：未实现 method 抛 `UnsupportedOperationException`

**选择：** 每个未实现的 method 方法体仅一行：`throw new UnsupportedOperationException("not yet implemented; will be filled in by migrate-console-* change")`，JavaDoc 标注预期实现 change 名。

**理由：**
- 接口签名稳定 → 后续业务 change 不用扩接口，只填实现
- 未实现的调用立即抛 `UnsupportedOperationException`，比 NPE / 默默 noop 更清晰
- 方法上可加 `@Deprecated(forRemoval=false, since="not_yet")` 让 IDE 提示，可选

**备选：**
- 不声明（被否决：每加一个 method 要 review 接口，慢）
- 实现成 `return null` / `return Map.of()`（被否决：默默 noop 比立即报错更难调）

### 决策 6：HTTP 200 + 空 body → 错误（与 Python 一致）

**选择：** `RegionApiResponseProcessor` 检测到 status=200 但 body 为空（或 body 不是合法 JSON）时，抛 `RegionApiException(500, "request region api body is nil", "集群请求网络异常")`。

**理由：** Go 后端任何成功响应都带 `{code,msg,data}`，空 body 表示中间网关或 region 端异常；忽略它会让上层拿到 null 然后 NPE，难调。

### 决策 7：HTTP 状态码与业务 code 解耦（与 console 自身契约一致）

**选择：** `RegionApiException.code` 是业务码（来自 region 响应 body 的 `code` 字段），`httpStatus` 仅作 debug 信息保留，不传到对外响应。

**理由：** 与 `migrate-console-response-contract` 保持一致：HTTP 一律 200，业务码走响应体 `code` 字段。

**实现：** `GlobalExceptionHandler` 追加 region 异常族的 mapping，统一从 `RegionApiException.getCode()` 取业务码而非 HTTP 状态。

### 决策 8：错误消息汉化覆盖范围 = Python 已实现的三种

**选择：** `RegionErrorMsgEnricher` 仅实现 Helm 接管冲突 / 域名冲突 / 频繁操作短语三种模式（与 Python `build_region_error_msg_show` 一致）。其他错误消息原样透传到 `msg_show`。

**正则（直接照搬 Python）：**

```java
HELM_OWNERSHIP_CONFLICT_RE = "(?i)(?<kind>[A-Za-z]+)\\s+\"(?<name>[^\"]+)\"\\s+in namespace\\s+\"(?<namespace>[^\"]+)\"\\s+exists and cannot be imported into the current release: invalid ownership metadata;.*?meta\\.helm\\.sh/release-name\":\\s+must be set to \"(?<release_name>[^\"]+)\";.*?meta\\.helm\\.sh/release-namespace\":\\s+must be set to \"(?<release_namespace>[^\"]+)\""

DOMAIN_CONFLICT_RE = "(?i)domain conflict:\\s+domain\\s+'(?<domain>[^']+)'\\s+conflicts with existing domain\\s+'(?<existing_domain>[^']+)'\\s+in namespace\\s+'(?<namespace>[^']+)'\\s+\\(resource:\\s+(?<resource>[^)]+)\\)"
```

**理由：** 100% 兼容 Python 当前行为；后续如要扩充其他模式，开新 change 增量加入。

### 决策 9：region_info 表只读（JdbcTemplate vs JPA Entity）

**选择：** `JdbcTemplate` 直读 + 手写 RowMapper。

**理由：**
- 本 change 只读不写，JdbcTemplate 简洁
- JPA entity 需要严格命名匹配（snake_case → field 注解一堆 `@Column`），且 `region_type` 字段是 JSON 字符串需要自定义 converter，徒增复杂度
- region_info 写操作的 entity 化留给 `migrate-console-region-cluster`，那时再统一引入 `@Entity`

### 决策 10：socket 错误重试 1 次（轻量）

**选择：** `RestClient` 的 ResponseErrorHandler 检测到 `IOException`/`SocketException`/`SocketTimeoutException`（包装在 `ResourceAccessException` 里）时，重试 1 次；4xx/5xx 业务错误不重试。

**理由：**
- Python 端的 `urllib3.exceptions.MaxRetryError` 处理本来就是单次 retry 等价
- 不引入 spring-retry 是为了避免在基础设施层引入额外切面/AOP 配置

## Risks / Trade-offs

- **未实现 method 数量大**（360+） → 后续每个业务 change 都要在 task 列表里包含"实现自己用到的 N 个 region method"。已在决策 4 列出资源域 → change 映射表，让后续 propose 阶段易识别 scope
- **PEM 解析复杂度**（私钥可能是 PKCS#1 / PKCS#8 / OpenSSH 多种格式）→ 实施期可能发现某种格式 BouncyCastle 处理不优雅；缓解：先支持 PKCS#8（最常见），如遇 PKCS#1 在实施期再补 PEMParser
- **MockRestServiceServer 不模拟 mTLS 握手** → 单元测试只能覆盖 HTTP 层逻辑，mTLS 装配靠 `RegionClientFactoryTest` 单独验证 KeyStore 生成正确即可；端到端 mTLS 验证留给跨链路联调
- **GlobalExceptionHandler 改动可能影响已通过的 ContractIntegrationTest** → 追加 region 异常分支不改原有分支顺序；回归测试覆盖
- **接口签名稳定性** → 一旦发布，未实现 method 的签名后续不能轻易改（否则后续业务 change 需要批量改）；缓解：propose 阶段 method 签名要"先看 Python 实际调用形态"再下笔，不能凭直觉

## Migration Plan

无在线迁移：

1. 实现基础设施（factory、mTLS、processor、异常族、enricher）
2. 14 个资源域接口骨架（带 JavaDoc）+ 默认抛 `UnsupportedOperationException` 实现类
3. TenantOperations 5 method 完整落地 + DTO
4. 修改 `GlobalExceptionHandler` 追加 region 异常族 mapping
5. 测试覆盖 + 回归 ContractIntegrationTest
6. 部署：生产环境必须设 `kuship.region.ssl-verify=true` + 确保 region_info 表已存在合法证书

**回滚策略：** 删除 `infrastructure/region/` 目录 + 还原 `GlobalExceptionHandler` 即可；本 change 不写 region_info 表。

## Open Questions

- WebSocket（`wsurl`）支持何时落？建议 `migrate-console-app-runtime`（实时日志/event 流必须 WS）
- region_info 的 entity 化（`@Entity`）何时做？建议 `migrate-console-region-cluster`（增删改集群必须 JPA）
- `kuship.region.ssl-verify` 默认 `false`（与 Python 一致）；生产部署必须 `true`，CLAUDE.md 文档化
- `enterprise_id` 来源：本 change 假定上层 service 显式传入；后续业务 change 接 user 表后从 `RequestContext.enterpriseId` 取
- 错误码扩展机制（如未来 Go 后端新增 `tenant_quota_storage_lack`）：暂不引入插件化映射表，按需在 `RegionApiResponseProcessor` 添加 if-else 分支即可（与 Python 一致）
