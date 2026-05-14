# Tasks — migrate-console-gateway-certificate

## 1. Schema 真相校验 + Entity

- [ ] 1.1 `docker exec kuship-mysql mysql ... DESC service_domain_certificate` 校验 8 列（已确认：ID / tenant_id / certificate_id varchar(50) / private_key longtext / certificate longtext / certificate_type longtext / create_time / alias varchar(64)）  <!-- 需用户联动验证 -->
- [x] 1.2 新建 `modules/gateway/cert/entity/ServiceDomainCertificate.java`：`@Table(name = "service_domain_certificate")`，PK `Integer ID`（注意大写列名），全部 8 列映射，`certificate` 与 `certificateType` 字段类型 `String`（columnDefinition longtext）
- [x] 1.3 新建 `ServiceDomainCertificateRepository`：
  - `Optional<ServiceDomainCertificate> findByTenantIdAndCertificateId(String tenantId, String certificateId)`
  - `Page<ServiceDomainCertificate> findByTenantIdAndAliasContainingIgnoreCase(String tenantId, String search, Pageable p)`
  - `Page<ServiceDomainCertificate> findByTenantId(String tenantId, Pageable p)`（无搜索）
  - `boolean existsByTenantIdAndAlias(String tenantId, String alias)`
  - `Optional<ServiceDomainCertificate> findById(Integer pk)`（JpaRepository 自带，仅声明使用）
- [x] 1.4 校验：依赖 `migrate-console-gateway-domain` 已扩 `ServiceDomain.certificateId` 字段（决策 4）；gateway-domain 尚未在 worktree 中落地，改用 `ServiceDomainCertificateRepository.countByCertificateId` native query 临时方案（`@Query(nativeQuery=true)`）

## 2. X.509 证书工具

- [x] 2.1 新建 `modules/gateway/cert/CertificateAnalyzer.java`：
  - `record CertInfo(String issuer, String subject, List<String> issuedTo, Instant validFrom, Instant validTo, String signatureAlgorithm, int publicKeySize)`
  - `CertInfo analyze(String pem)` —— 用 `CertificateFactory.getInstance("X.509").generateCertificate(...)`；SAN 走 `cert.getSubjectAlternativeNames()`；公钥位数从 `RSAPublicKey.getModulus().bitLength()` 或 `ECPublicKey.getParams().getCurve()`
  - `void validatePair(String certPem, String privateKeyPem)` —— 解证书拿 publicKey，解私钥拿 privateKey；RSA 比 modulus、ECDSA 比公钥派生（k × G）；不匹配抛 `ServiceHandleException(400, ...)`
  - 私钥解析：先尝试 PKCS#8（`PKCS8EncodedKeySpec`），失败 fallback BouncyCastle 解 PKCS#1 RSA（项目已有 BC 依赖）
- [x] 2.2 单测 `CertificateAnalyzerTest`：覆盖 design.md 列出的 5 类用例（RSA / ECDSA / 不匹配 / PKCS#1 / 过期）+ 2 边界用例（空 cert / 空 key）共 9 用例
- [x] 2.3 测试夹具 `CertGenerator`（test 目录工具）：用 BouncyCastle 现场生成 self-signed RSA / ECDSA 证书 + key，避免在源码中放真实 PEM 或泄露的测试证书

## 3. Region API 接口扩展

- [x] 3.1 校验 `infrastructure/region/api/GatewayOperations.java` 中 5 个 default 占位 method 已存在（getCertificate / createCertificate / updateCertificate / deleteCertificate / updateIngressesByCertificate）；接口方法命名与 design.md 中不同（无 "Gateway" 前缀），沿用已有接口命名
- [x] 3.2 在 `infrastructure/region/api/GatewayOperationsImpl.java`（已 `@Primary`）实现 5 个 method：
  - 路径表见 design.md "Region API URL 表"
  - `updateIngressesByCertificate` 路径段需要 `region_tenant_name`，从 `TenantsRepository.findByTenantName(...).getNamespace()` 取（注入 TenantsRepository）
  - 实现使用 RestClient 直接调用（GatewayOperationsImpl 自带 exchangeWithRetry，无需 RegionApiSupport）
- [x] 3.3 单测 `GatewayOperationsImplTest`：用 `MockRestServiceServer` 断言 5 method 的 URL 路径 + body 形状 + region 5xx 透传（6 用例）

## 4. Service 层

- [x] 4.1 新建 `modules/gateway/cert/service/CertificateService.java` `@Service`：注入 `ServiceDomainCertificateRepository` / `TenantsRepository` / `GatewayOperations` / `CertificateAnalyzer`（ServiceDomainRepository 替代为 native query 方案）
- [x] 4.2 实现 `Page<Map<String, Object>> listCertificates(String tenantId, String searchKey, int page, int pageSize)`：
  - 调 repo 分页查询
  - 对每行 `Base64.decode(cert.certificate)` 解 PEM → `CertificateAnalyzer.analyze` 拼装列表项（含 alias / certificate_type / id / issuer / subject / valid_from / valid_to / issued_to）
- [x] 4.3 实现 `ServiceDomainCertificate addCertificate(...)` （alias 校验 + 重名检查 + validatePair + UUID + Base64 编码 + @Transactional + gateway 类型 region 调用）
- [x] 4.4 实现 `Map<String, Object> getCertificate(Integer pk)`（详情含原文 PEM）
- [x] 4.5 实现 `void deleteCertificate(String regionName, Tenants tenant, Integer pk)`（引用检查 + 先 region 后本地）
- [x] 4.6 实现 `ServiceDomainCertificate updateCertificate(...)` （alias 重名 + validatePair + @Transactional + 3 种类型切换 region 调用）
- [x] 4.7 实现 `String checkCertificate(Integer certId, String domainName)`（SAN 解析 + 通配符匹配）
- [x] 4.8 单测 `CertificateServiceTest`：mock 各依赖，断言 add/update/delete/check 的 region 调用次数与 body 形状（9 用例）

## 5. Controller 落地

- [x] 5.1 新建 `modules/gateway/cert/controller/TenantCertificateController.java`：
  - `GET /console/teams/{team_name}/certificates(?page_num=&page_size=&search_key=)` → `service.listCertificates(...)` 包装为 `general_message{list, bean.nums}`
  - `POST /console/teams/{team_name}/certificates` body=`{alias, private_key, certificate, certificate_type}` → `service.addCertificate` 返 `bean={alias, id}`
  - trailing slash 兼容
  - `@RequirePerm(PermCode.TEAM_CERTIFICATE)`
- [x] 5.2 新建 `TenantCertificateManageController.java`：
  - `DELETE /console/teams/{team_name}/certificates/{certificate_id}` → `service.deleteCertificate(region, tenant, pk)`
  - `PUT /console/teams/{team_name}/certificates/{certificate_id}` body=`{alias, private_key, certificate, certificate_type}` → `service.updateCertificate`
  - 注意：rainbond 此 view 实际**没有 GET method**，本 change 同样不实现 GET
- [x] 5.3 新建 `CalibrationCertificateController.java`：
  - `POST /console/teams/{team_name}/calibration_certificate` body=`{certificate_id, domain_name}` → `service.checkCertificate(...)` 返 `bean={is_pass: "pass"|"un_pass"}`
- [x] 5.4 新建 `EnterpriseCertificateController.java`：
  - `POST /console/enterprise/team/certificate` 占位返 `bean={is_certificate: 1}`，与 rainbond `team.py:CertificateView` 行为一致；不调 region；`@RequireEnterpriseAdmin`

## 6. 集成测试

- [ ] 6.1 `TenantCertificateControllerIntegrationTest`：
  - 6.1.1 上传 RSA 2048 self-signed cert (test 用 `CertGenerator` 生成) → 200，`bean.id` 存在  <!-- 需用户联动验证（需真实 MySQL） -->
  - 6.1.2 私钥不匹配 → 400  <!-- 需用户联动验证 -->
  - 6.1.3 alias 重名 → 409  <!-- 需用户联动验证 -->
  - 6.1.4 列表搜索：插入 3 条 alias = `prod-a/prod-b/dev-c`，`?search_key=prod` 返 2 条  <!-- 需用户联动验证 -->
- [ ] 6.2 `TenantCertificateManageControllerIntegrationTest`：  <!-- 需用户联动验证 -->
  - 6.2.1 删除时被 `service_domain.certificate_id` 引用 → 409
  - 6.2.2 更新 certificate_type 从 "服务端证书" → "gateway"，断言 `@MockitoBean GatewayOperations.createGatewayCertificate` 被调用一次
  - 6.2.3 更新 certificate_type 从 "gateway" → "服务端证书"，断言 `deleteGatewayCertificate` 被调用一次
- [ ] 6.3 `CalibrationCertificateControllerIntegrationTest`：  <!-- 需用户联动验证 -->
  - 6.3.1 SAN=`*.foo.com`，校验 `bar.foo.com` → `is_pass=pass`
  - 6.3.2 SAN=`*.foo.com`，校验 `foo.com` → `is_pass=un_pass`（通配不匹配根域）
  - 6.3.3 SAN=`a.com,b.com`，校验 `c.com` → `un_pass`

## 7. 文档与归档

- [x] 7.1 `CLAUDE_FRAGMENT.md` 写到 `openspec/changes/migrate-console-gateway-certificate/CLAUDE_FRAGMENT.md`（不修改 `kuship-console/CLAUDE.md`）
- [ ] 7.2 路线图 `migrate-region-coverage-roadmap` 的 Requirement 表中把本 change 行标注完成（归档时执行）

## 8. 编译 / 重启 / 联动验证

- [x] 8.1 `cd kuship-console && mvn -DskipTests package` 通过（24 个新增测试全通过）
- [ ] 8.2 重启 console；上传一份测试证书 (`POST /console/teams/default/certificates`)  <!-- 需用户联动验证 -->
- [ ] 8.3 在 `gateway-domain` change 落地的 `service_domain` 表里手工 INSERT 一行，`certificate_id` 引用刚上传的证书 PK；尝试删除该证书 → 应返 409  <!-- 需用户联动验证 -->
- [ ] 8.4 删除该 service_domain 行，再删证书 → 200；本地证书表行消失，gateway 类型时 region GatewayTLS 被同步删  <!-- 需用户联动验证 -->
