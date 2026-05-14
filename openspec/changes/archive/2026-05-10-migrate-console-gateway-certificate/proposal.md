## Why

`migrate-console-gateway-domain` 落地后用户能 CRUD HTTP/TCP 路由，但绑 HTTPS 域名时第一步是选证书。kuship-console 当前 0 个证书 endpoint：

- `GET/POST /console/teams/{team_name}/certificates` 列表与上传无 handler
- `GET/PUT/DELETE /console/teams/{team_name}/certificates/{certificate_id}` 单条管理无 handler
- `POST /console/teams/{team_name}/calibration_certificate` 证书与域名匹配校验无 handler
- `POST /console/enterprise/team/certificate` 企业级证书占位接口无 handler

UI 表现：用户在域名页选"启用 HTTPS"后弹出的"证书选择"下拉为空，HTTPS 流程整路断。

`migrate-region-coverage-roadmap` 把这块归为 **P0 #3**（5 region method + 1 个本地表 + ~7 endpoint），与 gateway-domain 紧接，是 P0 路线起点之后第二步。本 change 完整迁移 rainbond `console/views/app_config/app_domain.py:61-298,490-498` + `console/services/app_config/domain_service.py:41-180` + `console/services/gateway_api.py:27-47` 的证书子域。

## What Changes

### 新增 controller（4 个，~7 endpoint）

按 rainbond `console/urls/__init__.py` 行号锚点：

- `TenantCertificateController` (`/console/teams/{team_name}/certificates`) GET/POST — `urls.py:630`
- `TenantCertificateManageController` (`/console/teams/{team_name}/certificates/{certificate_id}`) GET/PUT/DELETE — `urls.py:631-632`
- `CalibrationCertificateController` (`/console/teams/{team_name}/calibration_certificate`) POST — `urls.py:655`
- `EnterpriseCertificateController` (`/console/enterprise/team/certificate`) POST — `urls.py:932`，rainbond Python 实现仅占位返 `{is_certificate: 1}`，本 change 同样占位以维持契约

### 扩 Region Operations 接口

扩 `GatewayOperations`（`infrastructure/region/api/`）+5 method（路线图决策 4 已为 gateway-certificate 预留）：
- `getGatewayCertificate(regionName, tenantName, body)` → GET `/v2/tenants/{tenant_name}/gateway-certificate`
- `createGatewayCertificate(regionName, tenantName, body)` → POST `/v2/tenants/{tenant_name}/gateway-certificate`
- `updateGatewayCertificate(regionName, tenantName, body)` → PUT `/v2/tenants/{tenant_name}/gateway-certificate`
- `deleteGatewayCertificate(regionName, tenantName, namespace, name)` → DELETE `/v2/tenants/{tenant_name}/gateway-certificate?namespace=&name=`
- `updateIngressesByCertificate(regionName, tenantName, body)` → PUT `/v2/tenants/{region_tenant_name}/gateway/certificate`（路径段用 region_tenant_name，与其他 4 个 method 不同）

5 个 method 已在 `GatewayOperations.java` 中以 default 占位形式存在（`migrate-console-region-cluster` 时一并埋桩），本 change 仅在 `GatewayOperationsImpl @Primary` 中落地 override。

### 新增 entity / repository

- `ServiceDomainCertificate`（`service_domain_certificate` 表，8 列：ID / tenant_id / certificate_id varchar 50（console UUID）/ private_key longtext / certificate longtext（**base64 编码后的 PEM**）/ certificate_type longtext / create_time / alias varchar 64）
- `ServiceDomainCertificateRepository`：`findByTenantIdAndCertificateId` / `findByTenantIdAndAliasContaining` / `findByCertificateId` / `existsByTenantIdAndAlias` / 分页搜索

### 业务规则迁移

按 `domain_service.py:41-180` + `gateway_api.py:27-47` 移植：

- `addCertificate(tenant, alias, certId, certPem, privateKey, certType)`：
  - alias 重名校验
  - X.509 证书有效性校验（公钥 / 私钥匹配；过期；编码合法性）—— Java 端用 `java.security.cert.CertificateFactory` 解析，`KeyPair` 比对模数（modulus）确保私钥匹配
  - 证书 PEM base64 编码后落库
  - 当 `certificateType == "gateway"` 时，**额外**调 region `createGatewayCertificate`（创建 GatewayAPI K8s GatewayTLS 资源）
- `getCertificate(tenant, page, page_size, search_key)`：本地分页查询 + Base64 解码后用 X.509 解析 issuer / subject / 有效期 / SAN，拼装为列表返
- `getCertificateByPk(pk)`：详情，返回原始 PEM（`base64Decode(certificate)`）+ private_key + 解析后元信息
- `deleteCertificateByPk(region, tenant, pk)`：
  - 先查 `service_domain` 是否仍引用此 certificate_id（rainbond 行为：仍被引用时拒绝删除）
  - 再删本地行；若 `certificateType == "gateway"`，调 region `deleteGatewayCertificate`
- `updateCertificate(region, tenant, certId, alias, certPem, privateKey, certType)`：
  - alias 改变时检查重名
  - certificate / certificate_type 变化时，gateway 类型走 region `updateGatewayCertificate`
  - 落库时同样 Base64 编码
- `checkCertificate(certId, domainName)` (`POST /calibration_certificate`)：
  - 取 cert PEM 解析 SAN
  - 通配符 `*.foo.com` 转 `.foo.com` 后做 endsWith 匹配
  - 返回 `pass` / `un_pass`

### X.509 证书解析工具

新建 `modules/gateway/cert/CertificateAnalyzer.java`（沿用 `LegacyPasswordEncoder` 风格，纯 JDK 实现，避免引 BouncyCastle）：
- 输入：PEM 文本
- 输出：`{issuer, subject, issued_to: List<String> SAN, valid_from, valid_to, signature_algorithm, public_key_size}`
- `validatePair(certPem, privateKeyPem)`：解 RSA / ECDSA 公钥 → 比 modulus / curve 与私钥匹配
- 不在本 change 内：证书链校验（leaf → CA 信任路径）、CRL / OCSP 检查 —— 留给独立 hardening

### 不在本 change 内（明确推迟 / 切出）

- 证书加密落盘（rainbond 也是明文 longtext）—— 独立 `harden-certificate-encryption` change
- 证书自动续期（cert-manager 集成）—— 不在路线图
- 应用维度证书查询（rainbond Python 也未实现）
- 监控视角证书过期告警 —— 留给 monitor-extras

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "网关证书 CRUD 与域名校验"。覆盖 4 controller / ~7 endpoint 的契约、`service_domain_certificate` 表 JPA 校验 + 本地写约束、gateway 类型证书的 region 双写两阶段事务、X.509 证书校验语义（公钥/私钥匹配 + SAN/CN 与 domain 匹配）。

## Impact

- **代码新增**：
  - controller：4 个 (`modules/gateway/controller/cert/`)
  - service：`CertificateService`、`CertificateAnalyzer`（X.509 工具）
  - region API：扩 `GatewayOperations` 5 method 实现（接口 default 已存在）
  - entity：`ServiceDomainCertificate` + repository
  - 单测：`CertificateAnalyzerTest`（含通配符 / SAN / 过期 / 公钥不匹配 4 类用例）+ `CertificateServiceTest` + 4 个 controller 集成测试
- **数据库**：`service_domain_certificate` 表 schema 由 rainbond Django migrations 拥有（kuship 仅 `validate`）
- **依赖**：不引入新 maven 依赖（用 JDK 自带 `java.security.cert.X509Certificate` + `KeyFactory`）
- **跨 change 衔接**：
  - `gateway-domain` change 已落地的 `ServiceDomain.certificateId` 字段，本 change 之后才有真实证书可绑（之前 UI 下拉为空）
  - `accesses` 字段 access_urls 在协议为 https 的 HTTP 规则下走 `https://` 渲染（前两轮已实现），本 change 落地后 https 域名才有完整闭环
- **不影响**：rainbond-console（仍可独立跑 7070）、其他已迁移 change
