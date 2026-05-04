## 1. 配置项与依赖

- [x] 1.1 在 `pom.xml` 把 `httpclient5` 从 `<optional>true</optional>` 改为默认依赖；同步移除 `<optional>` 标签
- [x] 1.2 在 `pom.xml` 增加 BouncyCastle Provider（`bcprov-jdk18on`）+ BouncyCastle PKIX（`bcpkix-jdk18on`）依赖，用于 PEM 私钥解析（PKCS#8 / PKCS#1）
- [x] 1.3 在 `application.yaml` 增加 `kuship.region.*` 配置段：`timeout-seconds: 5`、`ssl-verify: false`、`connection-pool-max-per-route: 0`（0 = cpu*5 自动）、`frequent-operation-messages: ["操作过于频繁，请稍后再试", "wait a moment please", "just wait a moment"]`
- [x] 1.4 创建 `cn.kuship.console.infrastructure.region.RegionProperties`（@ConfigurationProperties("kuship.region")）映射上述配置

## 2. region_info 表只读访问

- [x] 2.1 创建 `cn.kuship.console.infrastructure.region.repository.RegionInfoDto` record：字段 `regionId`/`regionName`/`regionAlias`/`regionType`/`url`/`wsurl`/`httpdomain`/`tcpdomain`/`token`/`status`/`scope`/`sslCaCert`/`certFile`/`keyFile`/`enterpriseId`/`provider`/`providerClusterId` 等
- [x] 2.2 创建 `RegionInfoRepository`（@Repository）：用 `JdbcTemplate` 提供 `findByName(String regionName)` 与 `findByEnterpriseAndName(String enterpriseId, String regionName)` 方法，返回 `Optional<RegionInfoDto>`；不写入
- [x] 2.3 创建 `RegionInfoRowMapper implements RowMapper<RegionInfoDto>`：snake_case 列名 → camelCase 字段映射

## 3. mTLS 证书装配

- [x] 3.1 创建 `cn.kuship.console.infrastructure.region.client.PemMaterialResolver`：判断输入是 PEM 内联文本（以 `-----BEGIN` 开头）还是文件路径（以 `/` 开头），统一返回字节数组
- [x] 3.2 创建 `KeyStoreFactory`：`createKeyStore(String certPem, String keyPem)` 用 BouncyCastle `PEMParser` 解析私钥（PKCS#8 优先，PKCS#1 兜底）+ JDK `CertificateFactory.getInstance("X.509")` 解析证书 → 构造 `KeyStore.getInstance("PKCS12")` 装入 cert+key 条目；`createTrustStore(String caPem)` 类似
- [x] 3.3 创建 `SslContextFactory`：根据 `RegionInfoDto` + `RegionProperties` 返回 `javax.net.ssl.SSLContext`：
    - 若 `ssl-verify=false` → trust-all `TrustManager`
    - 若 `ssl-verify=true` → 使用 trust store
    - 始终装入 client cert key store（用于双向认证）
- [x] 3.4 单元测试 `KeyStoreFactoryTest`：用测试用 PEM（self-signed CA + cert + key）验证内联与文件两种形式都能装配

## 4. RegionClientFactory（核心）

- [x] 4.1 创建 `RegionClientKey` record：`(String enterpriseId, String regionName)`
- [x] 4.2 创建 `cn.kuship.console.infrastructure.region.client.RegionClientFactory`（@Component）：
    - 内部 `ConcurrentHashMap<RegionClientKey, RestClient>` 缓存
    - 公开 `getClient(String regionName, String enterpriseId)` 方法：`computeIfAbsent` 内调 `RegionInfoRepository` + `SslContextFactory` + `HttpClientBuilder`（HttpClient5）+ `PoolingHttpClientConnectionManager`（maxPerRoute=cpu*5 / 配置值）+ socket/connect timeout 5s → 构造 `RestClient.builder().requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient)).baseUrl(regionInfo.url()).build()`
    - 公开 `evict(String regionName, String enterpriseId)` 主动失效
    - region 不存在时抛 `RegionApiException(500, "region not found: <name>", "集群配置不存在")`
- [x] 4.3 单元测试 `RegionClientFactoryTest`：mock `RegionInfoRepository`，验证缓存命中、evict、不存在抛异常

## 5. 请求/响应处理 + 异常族

- [x] 5.1 创建 `cn.kuship.console.infrastructure.region.response.RegionApiResponse<T>` record：`code`/`msg`/`msgShow`/`bean(T)`/`list(List<?>)`，`@JsonProperty` 标注 snake_case
- [x] 5.2 创建 `cn.kuship.console.infrastructure.region.exception` 包，依次创建：
    - `RegionApiException extends RuntimeException`（字段 `apitype`/`url`/`method`/`httpStatus`/`code`/`msg`/`msgShow`/`bean`，构造器 + getter）
    - `RegionApiFrequentException extends RegionApiException`
    - `RegionApiSocketException extends RegionApiException`
    - `InvalidLicenseException extends RegionApiException`
    - `ClusterLackOfMemoryException extends RegionApiException`
    - `TenantLackOfMemoryException extends RegionApiException`
    - `TenantLackOfCpuException extends RegionApiException`
    - `TenantQuotaCpuLackException extends RegionApiException`
    - `TenantQuotaMemoryLackException extends RegionApiException`
    - `ClusterAuthLackOfMemoryException extends RegionApiException`
    - `ClusterAuthLackOfNodeException extends RegionApiException`
    - `ClusterAuthLackOfLicenseException extends RegionApiException`
    - `ClusterAuthLackOfLicenseExpireException extends RegionApiException`
- [x] 5.3 创建 `RegionApiResponseProcessor`：
    - `<T> T extractBean(ResponseEntity<String> response, Class<T> beanType)` 与 `<T> List<T> extractList(...)`
    - 内部按 design.md 决策 6/7 + Python `_check_status` 完整逻辑判定异常类型：HTTP 200+空 body、4xx-5xx+code、HTTP 401+bean.code=10400、HTTP 409+频繁操作短语、HTTP 412+9 个字面错误码
- [x] 5.4 单元测试 `RegionApiResponseProcessorTest`：6 个用例分别覆盖 标准成功 / HTTP 200空body / 4xx含code / 401+10400 / 409频繁 / 412各字面错误码

## 6. 错误消息中文化

- [x] 6.1 创建 `cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher`：
    - 静态正则常量 `HELM_OWNERSHIP_CONFLICT_RE`、`DOMAIN_CONFLICT_RE`（直接照搬 Python 正则，转 Java 语法）
    - `enrich(String msg)` → `String msgShow`
    - 优先匹配 Helm 冲突 → 域名冲突 → 默认透传 msg
- [x] 6.2 单元测试 `RegionErrorMsgEnricherTest`：6 个用例（每种模式 2 个：完整捕获 + 兜底）

## 7. 14 个资源域接口骨架（仅签名 + JavaDoc）

- [x] 7.1 创建 `cn.kuship.console.infrastructure.region.api.TenantOperations` 接口：5 个 method 签名 + 完整 JavaDoc（含对应 Python `regionapi.py` line 引用）
- [x] 7.2 创建 `ServiceOperations` 接口：service CRUD / build / code_check / language（约 20 method）。JavaDoc 标注实现 change=`migrate-console-app-create`
- [x] 7.3 创建 `ServiceDependencyOperations` 接口（约 8 method）→ `migrate-console-application-core`
- [x] 7.4 创建 `ServiceEnvOperations` 接口（约 6 method）→ `migrate-console-application-core`
- [x] 7.5 创建 `ServicePortOperations` 接口（约 12 method）→ `migrate-console-application-core`
- [x] 7.6 创建 `ServiceVolumeOperations` 接口（约 15 method）→ `migrate-console-application-core`
- [x] 7.7 创建 `ServiceProbeOperations` 接口（约 6 method）→ `migrate-console-application-core`
- [x] 7.8 创建 `ServiceLifecycleOperations` 接口：start/stop/restart/build/upgrade/rollback/scale/pause/un_pause（约 15 method）→ `migrate-console-app-runtime`
- [x] 7.9 创建 `ServiceStatusOperations` 接口：service_status / abnormal_status / pods / pod_detail（约 12 method）→ `migrate-console-app-runtime`
- [x] 7.10 创建 `ServiceLogOperations` 接口：logs / log_files / docker_log_instance（约 8 method）→ `migrate-console-app-runtime`
- [x] 7.11 创建 `EventOperations` 接口：event_log / target_events / myteams_events（约 8 method）→ `migrate-console-app-runtime`
- [x] 7.12 创建 `HelmOperations` 接口：chart info / yaml / upload chart / upload chart resource/value（约 10 method）→ `migrate-console-app-market`
- [x] 7.13 创建 `GatewayOperations` 接口：certificate / ingress / route（约 15 method）→ `migrate-console-application-core` / `migrate-console-region-cluster`
- [x] 7.14 创建 `ClusterOperations` 接口：region info / labels / publickey 等（约 10 method）→ `migrate-console-region-cluster`

## 8. 14 个默认实现类（除 TenantOperations 外仅占位）

- [x] 8.1 创建 13 个 `*OperationsDefaultImpl` 实现类（@Service，仅 ServiceOperations 等非 Tenant 13 个），方法体一律：
    ```java
    throw new UnsupportedOperationException(
        "not yet implemented; will be filled in by " + IMPLEMENTING_CHANGE);
    ```
- [x] 8.2 每个实现类内 `IMPLEMENTING_CHANGE` 常量值与 JavaDoc 一致（如 `"migrate-console-app-create"`）

## 9. TenantOperations 完整实现

- [x] 9.1 创建 DTO：`CreateTenantReq`（record，含 `tenantName`/`tenantId`/`enterpriseId`/`namespace`/`bindExisting`），`@JsonProperty` 标注 snake_case 列名（`tenant_name` 等）
- [x] 9.2 创建 DTO：`TenantResourcesResp`（record，含 limit/used CPU/memory/disk 等字段），按 Python `regionapi.py:get_tenant_resources` 实际返回结构
- [x] 9.3 创建 DTO：`RegionPublickeyResp`（record，含 `publicKey` 字段）
- [x] 9.4 创建 DTO：`RegionLabelsResp`（record，含 labels Map）
- [x] 9.5 创建 `TenantOperationsImpl`（@Service）：通过构造器注入 `RegionClientFactory` + `RegionApiResponseProcessor`；实现 5 个 method，每个 method 用 `factory.getClient(...)` 拿 RestClient → 发请求 → `processor.extractBean(...)` 拿 DTO
- [x] 9.6 集成测试 `TenantOperationsIntegrationTest` 用 `MockRestServiceServer`：5 个 method 各一个用例（含 200 成功 + 一个 4xx 错误用例）

## 10. GlobalExceptionHandler 追加 region 异常映射

- [x] 10.1 在 `cn.kuship.console.common.exception.GlobalExceptionHandler` 追加：
    - `@ExceptionHandler(RegionApiException.class)` → 用 ex.getCode/getMsg/getMsgShow 构造响应（`msgShow` 经 `RegionErrorMsgEnricher` 兜底处理）
    - `@ExceptionHandler(RegionApiFrequentException.class)` → `code=429`，`msg_show="操作过于频繁，请稍后再试"`
    - `@ExceptionHandler(RegionApiSocketException.class)` → `code=503`，`msg_show="集群网络不可达"`
    - `@ExceptionHandler(InvalidLicenseException.class)` → `code=10400`，`msg_show="集群授权失效或未授权"`
    - `@ExceptionHandler(ClusterLackOfMemoryException.class)` → `code=412`，`msg="cluster_lack_of_memory"`，`msg_show="集群内存不足"`
    - `@ExceptionHandler(TenantLackOfMemoryException.class)` → `code=412`，`msg_show="团队内存配额不足"`
    - 其他 7 个资源不足异常对应映射（写中文 msg_show）
- [x] 10.2 单元测试覆盖每个新增 handler

## 11. 文档

- [x] 11.1 在 `kuship-console/CLAUDE.md` 增加新段 "Region API client"：
    - 14 个资源域接口列表 + 注入示例
    - 11 个异常类列表 + 各自语义
    - mTLS 配置说明（PEM 内联 vs 文件路径，生产建议 ssl-verify=true）
    - 后续业务 change 如何"就地填空"未实现 method 的指南
- [x] 11.2 README.md 不更新（本 change 不影响本地启动新功能）

## 12. 验收

- [x] 12.1 `mvn -pl kuship-console clean package` BUILD SUCCESS，所有新增测试通过，0 项目代码 warning
- [x] 12.2 启动应用（local profile），原有 `/console/healthz`、`/actuator/health`、`/console/teams`（401）、ContractIntegrationTest 全套行为不变（回归）
- [x] 12.3 `RegionClientFactoryTest` 单测覆盖：缓存命中、evict、内联 PEM 装配、文件 PEM 装配、region 不存在抛异常
- [x] 12.4 `RegionApiResponseProcessorTest` 6 个错误响应用例全部通过
- [x] 12.5 `RegionErrorMsgEnricherTest` 3 种汉化模式 + 兜底 全部通过
- [x] 12.6 `TenantOperationsIntegrationTest` 5 个 method × 1 个 200 用例 + 1 个 409 用例（验证 region 异常 → general_message 映射）通过
- [x] 12.7 调用 `serviceOperations.createService(...)` 等未实现 method 抛 `UnsupportedOperationException`，message 含 `"not yet implemented"` 与 `"migrate-console-app-create"`
- [x] 12.8 docker build 成功（基础镜像不变）
