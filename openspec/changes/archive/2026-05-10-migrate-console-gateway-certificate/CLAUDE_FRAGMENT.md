# CLAUDE_FRAGMENT — migrate-console-gateway-certificate

> 本文件补充 `kuship-console/CLAUDE.md` 的"网关证书 CRUD"子域说明。
> 不修改 `kuship-console/CLAUDE.md`，内容由归档流程合并。

## 网关证书 CRUD（migrate-console-gateway-certificate）

`cn.kuship.console.modules.gateway.cert` 落地网关证书 CRUD 与域名校验能力，
对齐 rainbond `console/views/app_config/app_domain.py:61-298,490-498`。

### Controllers（4 个，~7 endpoint）

| Controller | 路径 | 方法 | rainbond 锚点 |
|---|---|---|---|
| `TenantCertificateController` | `/console/teams/{team_name}/certificates` | GET / POST | `urls.py:630 TenantCertificateView` |
| `TenantCertificateManageController` | `/console/teams/{team_name}/certificates/{certificate_id}` | PUT / DELETE | `urls.py:631-632 TenantCertificateManageView` |
| `CalibrationCertificateController` | `/console/teams/{team_name}/calibration_certificate` | POST | `urls.py:655 CalibrationCertificate` |
| `EnterpriseCertificateController` | `/console/enterprise/team/certificate` | POST（占位） | `urls.py:932 CertificateView` |

- `TenantCertificateManageController` 仅 PUT / DELETE，无 GET（对齐 rainbond 无 get 方法行为）
- `EnterpriseCertificateController` 永远返回 `{is_certificate: 1}`，不调 region，预留给 enterprise 子 change

### service_domain_certificate 表说明

8 列映射（`ServiceDomainCertificate` entity）：

| 列名 | 类型 | 说明 |
|---|---|---|
| `ID` | INT（PK）| 大写，JPA `@Column(name="ID")` |
| `tenant_id` | varchar(32) | 租户 UUID |
| `certificate_id` | varchar(50) | console 生成的 32-char UUID |
| `private_key` | longtext | **原文 PEM，不编码** |
| `certificate` | longtext | **PEM 经 Base64 编码后存储** |
| `certificate_type` | longtext | 类型字符串，`"gateway"` 触发双写 |
| `create_time` | datetime | 创建时间 |
| `alias` | varchar(64) | 租户范围内唯一，≤64 字符 |

### Base64 编码约束（决策 1）

与 rainbond Python 端互操作的硬约束：

- **写入**：`certificate` 列 = `Base64.getEncoder().encodeToString(pemBytes)`
- **读取**：`Base64.getDecoder().decode(cert.getCertificate())` → PEM 明文
- `private_key` 列**直存原文 PEM**（不编码），与 rainbond 端跨服务读写兼容
- 日志和异常消息中**不**暴露 `private_key` / `certificate` 全文（安全约束）

### gateway 类型双写顺序（决策 5）

与 rainbond Python 端 **相反**（kuship 改进）：

| 操作 | kuship 顺序 | 说明 |
|---|---|---|
| 创建 | 先本地 INSERT（`@Transactional`）后 region `createCertificate` | region 失败 → 事务回滚本地行，无孤儿数据 |
| 更新 | 先 region → 后本地 UPDATE | 同事务，region 失败回滚 |
| 删除 | 先 region `deleteCertificate` → 后本地 DELETE | 释放 K8s GatewayTLS 资源后才删本地行 |

类型切换规则（`updateCertificate`）：
- 普通 → gateway：调 region `createCertificate`
- gateway → 普通：调 region `deleteCertificate`
- gateway → gateway：调 region `updateCertificate`

### CertificateAnalyzer（X.509 工具）

纯 JDK + BouncyCastle（项目已有 `bcprov-jdk18on` / `bcpkix-jdk18on`）：

- PKCS#8 私钥：`KeyFactory.getInstance("RSA"/"EC").generatePrivate(PKCS8EncodedKeySpec)`
- PKCS#1 RSA 私钥：BouncyCastle `RSAPrivateKey.getInstance(der)` → `RSAPrivateKeySpec`
- RSA 公私钥匹配：比对 `RSAPublicKey.getModulus()` 与 `RSAPrivateKey.getModulus()`
- ECDSA 公私钥匹配：BouncyCastle 标量乘法 `G × s` 与证书公钥点比对
- SAN 提取：`cert.getSubjectAlternativeNames()` type=2（dNSName）/ type=7（iPAddress）

### GatewayOperations 5 method 实现

`GatewayOperationsImpl`（已 `@Primary`）新增 5 个 override：

| method | HTTP | 路径 |
|---|---|---|
| `getCertificate` | GET | `/v2/tenants/{tenant_name}/gateway-certificate` |
| `createCertificate` | POST | `/v2/tenants/{tenant_name}/gateway-certificate` |
| `updateCertificate` | PUT | `/v2/tenants/{tenant_name}/gateway-certificate` |
| `deleteCertificate` | DELETE | `/v2/tenants/{tenant_name}/gateway-certificate?namespace=&name=` |
| `updateIngressesByCertificate` | PUT | `/v2/tenants/{region_tenant_name}/gateway/certificate`（namespace 路径段） |

注意 `updateIngressesByCertificate` 路径段用 `tenant.namespace`（region_tenant_name），
从 `TenantsRepository.findByTenantName(tenantName).getNamespace()` 取。

### 测试覆盖

| 测试类 | 类型 | 用例数 |
|---|---|---|
| `CertificateAnalyzerTest` | 纯单测 | 9 |
| `CertGenerator` | 测试夹具（无测试方法） | — |
| `CertificateServiceTest` | Mockito 单测 | 9 |
| `GatewayOperationsImplTest` | MockRestServiceServer 单测 | 6 |
| 集成测试（6.1-6.3） | 需用户联动（真实 DB） | 待运行 |

所有 24 个单测均通过（`mvn -DskipTests package` + `mvn -Dtest=... test`）。
