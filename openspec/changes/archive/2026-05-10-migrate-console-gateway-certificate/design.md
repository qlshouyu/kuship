# Design — migrate-console-gateway-certificate

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是 **P0 #3**，估计 method 数 **5**（远低于 ≤ 30 上限），但端点数 7 + 1 个工具类 + 1 张本地表，工作量约 1 周。归档时反向更新路线表对应行。

依赖：建议在 `migrate-console-gateway-domain` 完成（或至少 entity 扩字段、`ServiceDomain.certificateId` 列已就绪）后再开始本 change，原因见决策 4。

## Region API URL 表

| method                          | HTTP | 路径                                                                  | 说明                              |
|---------------------------------|------|-----------------------------------------------------------------------|-----------------------------------|
| getGatewayCertificate           | GET  | `/v2/tenants/{tenant_name}/gateway-certificate`                        | body 透传 namespace / name 过滤     |
| createGatewayCertificate        | POST | `/v2/tenants/{tenant_name}/gateway-certificate`                        | body: `{namespace,name,private_key,certificate}` |
| updateGatewayCertificate        | PUT  | `/v2/tenants/{tenant_name}/gateway-certificate`                        | 同上                              |
| deleteGatewayCertificate        | DELETE | `/v2/tenants/{tenant_name}/gateway-certificate?namespace=&name=`     | 仅 query string                    |
| updateIngressesByCertificate    | PUT  | `/v2/tenants/{region_tenant_name}/gateway/certificate`                | 路径段是 `region_tenant_name`，与上面 4 个不同 |

注意 `updateIngressesByCertificate` 的路径段是 region 端记录的 tenant name（namespace），与其他 4 个用 console 端 `tenant_name` 不同 —— rainbond Python `regionapi.py:1953-1958` 也明确做了这个区分（先 `__get_tenant_region_info` 再用 `region.region_tenant_name`）。kuship 沿用同一行为，需要 `TenantsRepository.findByTenantName(...).getNamespace()` 取出。

## Controller 路径锚点

| Controller                              | path                                                          | method            | rainbond 锚点                   |
|-----------------------------------------|---------------------------------------------------------------|-------------------|---------------------------------|
| TenantCertificateController             | `/console/teams/{team_name}/certificates`                      | GET / POST         | `urls.py:630` `TenantCertificateView` |
| TenantCertificateManageController       | `/console/teams/{team_name}/certificates/{certificate_id}`     | GET / PUT / DELETE | `urls.py:631-632` `TenantCertificateManageView` |
| CalibrationCertificateController        | `/console/teams/{team_name}/calibration_certificate`           | POST              | `urls.py:655` `CalibrationCertificate` |
| EnterpriseCertificateController         | `/console/enterprise/team/certificate`                         | POST              | `urls.py:932` `CertificateView`（占位） |

trailing slash 兼容：每 endpoint 同时声明 `path` 与 `path/`，与项目既定规则一致。

`TenantCertificateManageController` 的 `GET` 是详情查询；rainbond Python 把"详情"塞进了 `delete` 处理逻辑（先查再删），但 `urls.py:632` 同时挂了 `GET` 走 view 的 `get`（实际看 view 代码没 GET method —— 让我以为是只有 PUT/DELETE）—— 验证后实际只有 PUT/DELETE，本 change 不强制加 GET，与 rainbond 一致。

详情查询走 `TenantCertificateController` GET 列表搭配 `?certificate_id=X` 过滤即可。

修订：rainbond 的 `TenantCertificateManageView` 仅 `delete`（urls.py:151）+ `put`（urls.py:194），无 `get`。本 change 同此 —— 详情功能由列表 + 客户端过滤完成。

## 决策 1 — 证书 PEM 编码：Base64 与原文

rainbond 端 `service_domain_certificate.certificate` 列存的是 **PEM → Base64 编码后的字符串**（`base64.b64encode(pem.encode())`）；`private_key` 列存原文 PEM。

**决策**：kuship 端遵循同样行为：
- 入库前 `certificate` 列 `Base64.getEncoder().encodeToString(pemBytes)` 编码
- 出库时 `Base64.getDecoder().decode(...)` 解码回 PEM
- `private_key` 列直存原文不编码

这是与 rainbond 跨服务读写互操作的硬约束。**不优化**为统一编码 —— 那会让 rainbond Python 端无法读 kuship 写入的行。

## 决策 2 — X.509 证书解析：纯 JDK vs BouncyCastle

rainbond 用 Python `cryptography` 库解 X.509。Java 端有两个选择：
- **JDK 自带**：`java.security.cert.CertificateFactory` 解 X.509，`KeyFactory` 解 RSA/EC 私钥（PKCS#8 格式），无 BouncyCastle 依赖
- **BouncyCastle**：解 PKCS#1 / OpenSSL 格式更宽松，但项目已有 BC 依赖（`add-aliyun-sms` change 引入）

rainbond 接受的私钥格式覆盖 PKCS#1（`-----BEGIN RSA PRIVATE KEY-----`）和 PKCS#8（`-----BEGIN PRIVATE KEY-----`）。

**决策**：用 JDK + BouncyCastle（项目已有）配合 —— PKCS#8 用 JDK，PKCS#1 fallback 走 BC。封装在 `CertificateAnalyzer` 里，业务层不感知。

## 决策 3 — 证书 / 私钥匹配校验

rainbond `cert_is_effective(certificate, private_key)` 实现是用 cryptography 库分别加载，不显式做 modulus / curve 比对，仅依赖加载错误抛异常。这其实**漏校验**了"证书与私钥不是同一对"的场景。

**决策**：kuship 端比 rainbond 严格一档：
- 解 X.509 证书拿到 publicKey
- 解私钥拿到 privateKey
- RSA：比对 publicKey 的 modulus 与 privateKey 的 modulus
- ECDSA：比对 publicKey 的曲线 + 公钥派生（privateKey × G == publicKey）
- 不匹配抛 `ServiceHandleException(400, "certificate and private key do not match", "证书与私钥不匹配")`

这是用户体验改进（rainbond 用户经常错配 cert/key 上传，调试到很晚才发现），不破坏跨服务兼容性（仍能读 rainbond 历史数据，仅写时严一些）。

## 决策 4 — 证书引用计数（删除前检查）

rainbond `delete_certificate_by_pk` 先查 `service_domain` 是否仍有 `certificate_id == 待删 pk` 的行，有则抛 `err_still_has_http_rules`（"证书仍被 HTTP 规则使用，不能删除"）。

**决策**：kuship 沿用同样语义。需要 `ServiceDomainRepository.existsByCertificateId(Integer)` 方法（在 `migrate-console-gateway-domain` 任务 1.6 中加，本 change 复用）。**因此本 change 软依赖 gateway-domain 完成 entity 扩字段任务**（具体是 `ServiceDomain.certificateId` 字段已映射）。

如果 gateway-domain 未完，可以本 change 内临时做：用 `@Query("SELECT count(d) FROM ServiceDomain d WHERE d.certificateId = :id")` 不依赖 entity 字段映射也能过 —— 但 entity 已扩字段时直接 derived query 更整洁。本 change 假设 gateway-domain 先于本 change 落地。

## 决策 5 — gateway 类型证书的双写

rainbond 里 certificate_type 有两种值：
- 普通证书（任何非 "gateway" 字符串，如 "服务端证书"）—— 仅本地表，不调 region
- 网关证书（"gateway"）—— 本地表 + region GatewayTLS 资源

写顺序：rainbond 端是 **先 region 后本地**（`add_certificate` 先 `gateway_api.create_gateway_tls` 再 `domain_repo.add_certificate`）。但若 region 写成功本地写失败，会留下 region 端的 GatewayTLS 资源孤儿。

**决策**：kuship 端反过来 —— **先本地后 region**，理由：
- 本地写在 `@Transactional` 中，region 写失败时事务回滚本地行（不会有孤儿数据）
- region 写失败时本地行也回滚，对账总是干净的
- 这与 `migrate-console-gateway-domain` 决策 3 的 bind 路径一致（一致性高于"先验证 region 再落库"的微弱风险收益）

更新（`update_certificate`）的 certificate_type 从 "gateway" 切回非 gateway 时，要 region `delete_gateway_certificate`；反之要 region `create_gateway_certificate`。两种切换都在 service 层判分支。

## 决策 6 — 错误消息

证书相关错误码沿用 rainbond：
- `err_cert_name_exists` → `ServiceHandleException(409, "certificate alias already exists", "证书名称已存在")`
- `err_cert_not_found` → `(404, "certificate not found", "证书不存在")`
- `err_still_has_http_rules` → `(409, "certificate still in use", "证书仍被 HTTP 规则使用，不能删除")`

业务异常通过 `GlobalExceptionHandler` 自动映射为 general_message。region 异常透传 `RegionApiException`。

## 决策 7 — 不在本 change 加密私钥

`service_domain_certificate.private_key` 列是 longtext 明文存储。从安全视角应该加密（与 `helm_repo.password` 用 `AesGcmEncryptor` 类似处理）。

**决策**：本 change **不**加密，理由：
- rainbond 也是明文，加密会破坏跨服务读取
- 加密设计本身需要 schema 变更（增加 `private_key_encrypted` 列 + 迁移）
- 安全提升留作独立 `harden-certificate-encryption` change，与 rainbond-console 同步推进

## 非决策（明确不做）

- **不做** 证书链校验（leaf → CA 信任路径） —— 留给 hardening
- **不做** CRL / OCSP 在线吊销检查 —— 留给 hardening
- **不做** 自动续期（cert-manager / acme.sh 集成）
- **不在** 本 change 引入 BouncyCastle 新依赖（项目已有，不变更版本）

## 测试约定

集成测试（`@SpringBootTest + @ActiveProfiles({"local","contract-test"})`）覆盖：

- `TenantCertificateControllerTest`：
  - 上传普通证书 happy path（生成 self-signed RSA 2048 cert + key 配对）
  - 上传时私钥不匹配证书 → 400
  - 上传时 alias 重名 → 409
  - 列表分页 + 搜索（按 alias 模糊）
- `TenantCertificateManageControllerTest`：
  - 删除证书时仍被 `service_domain` 引用 → 409
  - 更新 certificate_type 从普通 → "gateway" 触发 region `createGatewayCertificate`
  - 更新 alias 重名 → 409
- `CalibrationCertificateControllerTest`：
  - SAN 含 `*.foo.com`，校验 `bar.foo.com` → pass
  - SAN 含 `bar.foo.com`，校验 `baz.foo.com` → un_pass
  - 通配符 `*.foo.com` 不匹配 `foo.com`（rainbond 行为）
- `CertificateAnalyzerTest`（纯单测）：
  - RSA 2048 公私钥匹配
  - ECDSA P-256 公私钥匹配
  - 公钥 modulus 不匹配 → false
  - 私钥 PKCS#1 / PKCS#8 双格式都能解
  - 过期证书 valid_to 解析正确

`@MockitoBean GatewayOperations` 替换 region 调用，断言入参 body 形状（namespace / name / private_key / certificate）。
