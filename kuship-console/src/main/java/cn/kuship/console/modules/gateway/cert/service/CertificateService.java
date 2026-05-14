package cn.kuship.console.modules.gateway.cert.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.GatewayOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.gateway.cert.CertificateAnalyzer;
import cn.kuship.console.modules.gateway.cert.CertificateAnalyzer.CertInfo;
import cn.kuship.console.modules.gateway.cert.entity.ServiceDomainCertificate;
import cn.kuship.console.modules.gateway.cert.entity.ServiceDomainCertificateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 网关证书业务服务。
 *
 * <p>对齐 rainbond {@code console/services/app_config/domain_service.py:41-180}
 * + {@code console/services/gateway_api.py:27-47}。
 *
 * <p><b>双写顺序决策（design.md 决策 5）</b>：写路径"先本地后 region"（事务回滚保本地）；
 * 删除路径"先 region 后本地"（确保 K8s 资源先释放）。
 *
 * <p><b>证书安全约束</b>：日志和异常消息中不暴露 private_key / certificate 全文。
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private static final String GATEWAY_CERT_TYPE = "gateway";
    private static final int MAX_ALIAS_LENGTH = 64;

    private final ServiceDomainCertificateRepository certRepo;
    private final TenantsRepository tenantsRepo;
    private final GatewayOperations gatewayOps;
    private final CertificateAnalyzer analyzer;

    public CertificateService(ServiceDomainCertificateRepository certRepo,
                               TenantsRepository tenantsRepo,
                               GatewayOperations gatewayOps,
                               CertificateAnalyzer analyzer) {
        this.certRepo = certRepo;
        this.tenantsRepo = tenantsRepo;
        this.gatewayOps = gatewayOps;
        this.analyzer = analyzer;
    }

    // ──────────────────────── 列表查询 ─────────────────────────────

    /**
     * 分页查询证书列表，每条记录附加 X.509 解析信息（issuer/subject/SAN/有效期）。
     *
     * @param tenantId  租户 ID
     * @param searchKey 按 alias 模糊搜索（可空）
     * @param page      1 基页码
     * @param pageSize  每页大小
     * @return 带分页的证书详情列表
     */
    public Page<Map<String, Object>> listCertificates(String tenantId, String searchKey,
                                                       int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("id").descending());
        Page<ServiceDomainCertificate> raw = (searchKey != null && !searchKey.isBlank())
                ? certRepo.findByTenantIdAndAliasContainingIgnoreCase(tenantId, searchKey, pageable)
                : certRepo.findByTenantId(tenantId, pageable);

        return raw.map(this::enrichCertInfo);
    }

    // ──────────────────────── 新增证书 ─────────────────────────────

    /**
     * 新增证书，对齐 rainbond {@code add_certificate}。
     *
     * <p>双写顺序（先本地后 region）：{@code @Transactional} 包裹本地 INSERT；
     * 若 {@code certificateType == "gateway"} 则在提交前调 region，region 失败抛异常事务回滚。
     *
     * @param regionName      区域名（gateway 类型时调用 region）
     * @param tenant          租户对象
     * @param alias           别名（租户范围内唯一，长度 ≤ 64）
     * @param certPem         证书 PEM 文本（明文，存库前 Base64 编码）
     * @param privateKey      私钥 PEM 文本（明文存库，日志不暴露）
     * @param certificateType 类型：{@code "gateway"} 或普通类型如 {@code "服务端证书"}
     * @return 新增的 {@link ServiceDomainCertificate} 实体
     */
    @Transactional
    public ServiceDomainCertificate addCertificate(String regionName, Tenants tenant,
                                                    String alias, String certPem,
                                                    String privateKey, String certificateType) {
        // 1. 入参校验
        if (alias == null || alias.isBlank()) {
            throw new ServiceHandleException(400, "alias is blank", "证书别名不能为空");
        }
        if (alias.length() > MAX_ALIAS_LENGTH) {
            throw new ServiceHandleException(400, "alias too long (max 64 chars)", "证书别名不能超过 64 个字符");
        }

        // 2. alias 重名校验
        if (certRepo.existsByTenantIdAndAlias(tenant.getTenantId(), alias)) {
            throw new ServiceHandleException(409, "certificate alias already exists",
                    "证书名称已存在");
        }

        // 3. 公私钥匹配校验
        analyzer.validatePair(certPem, privateKey);

        // 4. 构建实体（certificate 列 Base64 编码；private_key 直存）
        ServiceDomainCertificate cert = new ServiceDomainCertificate();
        cert.setTenantId(tenant.getTenantId());
        cert.setCertificateId(UUID.randomUUID().toString().replace("-", ""));
        cert.setAlias(alias);
        cert.setCertificate(Base64.getEncoder().encodeToString(certPem.getBytes()));
        cert.setPrivateKey(privateKey);
        cert.setCertificateType(certificateType);
        cert.setCreateTime(LocalDateTime.now());

        // 5. 先本地 INSERT（在事务内）
        ServiceDomainCertificate saved = certRepo.save(cert);

        // 6. gateway 类型额外调 region createCertificate（失败事务回滚）
        if (GATEWAY_CERT_TYPE.equals(certificateType)) {
            Map<String, Object> body = buildGatewayBody(tenant, alias, privateKey, certPem);
            gatewayOps.createCertificate(regionName, tenant.getTenantName(), body);
            log.info("[cert] createGatewayCertificate ok: tenantName={}, alias={}",
                    tenant.getTenantName(), alias);
        }

        return saved;
    }

    // ──────────────────────── 证书详情 ─────────────────────────────

    /**
     * 查询单条证书详情（含原文 PEM）。
     *
     * @param pk 主键 ID
     * @return 详情 Map（含解析信息）
     */
    public Map<String, Object> getCertificate(Integer pk) {
        ServiceDomainCertificate cert = requireCert(pk);
        Map<String, Object> detail = enrichCertInfo(cert);
        // 详情额外返回原文 private_key 与 certificate（Base64 解码）
        detail.put("private_key", cert.getPrivateKey());
        String decodedPem = decodeBase64Cert(cert.getCertificate());
        detail.put("certificate", decodedPem);
        return detail;
    }

    // ──────────────────────── 删除证书 ─────────────────────────────

    /**
     * 删除证书，对齐 rainbond {@code delete_certificate_by_pk}。
     *
     * <p>删除路径（先 region 后本地）：先调 region 释放 K8s GatewayTLS 资源，再删本地行。
     *
     * @param regionName 区域名
     * @param tenant     租户对象
     * @param pk         主键 ID
     */
    @Transactional
    public void deleteCertificate(String regionName, Tenants tenant, Integer pk) {
        ServiceDomainCertificate cert = requireCert(pk);

        // 1. 检查是否被 service_domain 引用
        long refCount = certRepo.countByCertificateId(pk);
        if (refCount > 0) {
            throw new ServiceHandleException(409, "certificate still in use",
                    "证书仍被 HTTP 规则使用，不能删除");
        }

        // 2. gateway 类型：先调 region 删除 GatewayTLS 资源
        if (GATEWAY_CERT_TYPE.equals(cert.getCertificateType())) {
            String namespace = tenant.getNamespace() != null ? tenant.getNamespace() : tenant.getTenantName();
            gatewayOps.deleteCertificate(regionName, tenant.getTenantName(), namespace, cert.getAlias());
            log.info("[cert] deleteGatewayCertificate ok: tenantName={}, alias={}",
                    tenant.getTenantName(), cert.getAlias());
        }

        // 3. 删本地行
        certRepo.deleteById(pk);
    }

    // ──────────────────────── 更新证书 ─────────────────────────────

    /**
     * 更新证书，对齐 rainbond {@code update_certificate}。
     *
     * <p>certificateType 切换时的双写逻辑：
     * <ul>
     *   <li>普通 → gateway：region createCertificate
     *   <li>gateway → 普通：region deleteCertificate
     *   <li>gateway → gateway：region updateCertificate
     * </ul>
     *
     * @param regionName      区域名
     * @param tenant          租户对象
     * @param pk              主键 ID
     * @param alias           新别名（null 表示不改）
     * @param certPem         新证书 PEM（null 表示不改）
     * @param privateKey      新私钥 PEM（null 表示不改）
     * @param certificateType 新类型（null 表示不改）
     * @return 更新后的实体
     */
    @Transactional
    public ServiceDomainCertificate updateCertificate(String regionName, Tenants tenant,
                                                       Integer pk, String alias,
                                                       String certPem, String privateKey,
                                                       String certificateType) {
        ServiceDomainCertificate cert = requireCert(pk);
        String oldType = cert.getCertificateType();
        String newType = certificateType != null ? certificateType : oldType;
        String newAlias = alias != null ? alias : cert.getAlias();
        String newCertPem = certPem != null ? certPem : null;
        String newPrivateKey = privateKey != null ? privateKey : cert.getPrivateKey();

        // 别名改变时校验重名
        if (alias != null && !alias.equals(cert.getAlias())) {
            if (certRepo.existsByTenantIdAndAlias(tenant.getTenantId(), alias)) {
                throw new ServiceHandleException(409, "certificate alias already exists",
                        "证书名称已存在");
            }
        }

        // 提供新证书时校验公私钥匹配
        if (certPem != null && privateKey != null) {
            analyzer.validatePair(certPem, privateKey);
        } else if (certPem != null) {
            analyzer.validatePair(certPem, cert.getPrivateKey());
        }

        // region 双写（类型切换）
        boolean oldIsGateway = GATEWAY_CERT_TYPE.equals(oldType);
        boolean newIsGateway = GATEWAY_CERT_TYPE.equals(newType);
        String effectivePem = certPem != null ? certPem : decodeBase64Cert(cert.getCertificate());
        String effectiveKey = privateKey != null ? privateKey : cert.getPrivateKey();

        if (!oldIsGateway && newIsGateway) {
            Map<String, Object> body = buildGatewayBody(tenant, newAlias, effectiveKey, effectivePem);
            gatewayOps.createCertificate(regionName, tenant.getTenantName(), body);
        } else if (oldIsGateway && !newIsGateway) {
            String namespace = tenant.getNamespace() != null ? tenant.getNamespace() : tenant.getTenantName();
            gatewayOps.deleteCertificate(regionName, tenant.getTenantName(), namespace, cert.getAlias());
        } else if (oldIsGateway) {
            // gateway → gateway：更新
            Map<String, Object> body = buildGatewayBody(tenant, newAlias, effectiveKey, effectivePem);
            gatewayOps.updateCertificate(regionName, tenant.getTenantName(), body);
        }

        // 更新本地行
        if (alias != null) cert.setAlias(alias);
        if (certPem != null) cert.setCertificate(Base64.getEncoder().encodeToString(certPem.getBytes()));
        if (privateKey != null) cert.setPrivateKey(privateKey);
        if (certificateType != null) cert.setCertificateType(certificateType);

        return certRepo.save(cert);
    }

    // ──────────────────────── 证书与域名匹配校验 ─────────────────────────────

    /**
     * 校验证书是否覆盖指定域名（对齐 rainbond {@code CalibrationCertificate} view）。
     *
     * <p>通配符规则：{@code *.foo.com} 匹配 {@code bar.foo.com} 但不匹配 {@code foo.com}
     * （rainbond Python 端行为，通配符仅覆盖一级子域）。
     *
     * @param certId     证书 ID（整型主键）
     * @param domainName 待校验域名
     * @return {@code "pass"} 或 {@code "un_pass"}
     */
    public String checkCertificate(Integer certId, String domainName) {
        ServiceDomainCertificate cert = requireCert(certId);
        String pem = decodeBase64Cert(cert.getCertificate());
        CertInfo info = analyzer.analyze(pem);

        for (String san : info.issuedTo()) {
            if (matchesDomain(san, domainName)) {
                return "pass";
            }
        }
        return "un_pass";
    }

    // ──────────────────────── 内部工具 ─────────────────────────────

    /** 域名匹配：通配符 *.foo.com → .foo.com endsWith；精确匹配 equals。 */
    private boolean matchesDomain(String san, String domain) {
        if (san == null || domain == null) return false;
        String sanLower = san.toLowerCase().trim();
        String domainLower = domain.toLowerCase().trim();
        if (sanLower.startsWith("*.")) {
            // 通配符：转为 .foo.com 后做 endsWith，且 domain 必须有子域（不匹配根域）
            String suffix = sanLower.substring(1); // ".foo.com"
            return domainLower.endsWith(suffix) && domainLower.length() > suffix.length();
        }
        return sanLower.equals(domainLower);
    }

    /** 从数据库 Base64 编码的 certificate 字段解码回 PEM 文本。 */
    private String decodeBase64Cert(String encoded) {
        if (encoded == null) return "";
        try {
            return new String(Base64.getDecoder().decode(encoded));
        } catch (Exception e) {
            throw new ServiceHandleException(500, "failed to decode certificate from storage",
                    "证书数据解码失败");
        }
    }

    /** 按主键查询证书，不存在则抛 404。 */
    private ServiceDomainCertificate requireCert(Integer pk) {
        return certRepo.findById(pk)
                .orElseThrow(() -> new ServiceHandleException(404,
                        "certificate not found: id=" + pk, "证书不存在"));
    }

    /**
     * 拼装 gateway 类型证书操作的 region body。
     * 日志不打印 privateKey / certificate 内容。
     */
    private Map<String, Object> buildGatewayBody(Tenants tenant, String alias,
                                                   String privateKey, String certificate) {
        String namespace = tenant.getNamespace() != null ? tenant.getNamespace() : tenant.getTenantName();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("namespace", namespace);
        body.put("name", alias);
        body.put("private_key", privateKey);
        body.put("certificate", certificate);
        return body;
    }

    /** 拼装证书列表条目（含 X.509 解析信息）；解析失败时仍返回基础字段。 */
    private Map<String, Object> enrichCertInfo(ServiceDomainCertificate cert) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", cert.getId());
        item.put("alias", cert.getAlias());
        item.put("certificate_type", cert.getCertificateType());
        item.put("tenant_id", cert.getTenantId());
        item.put("certificate_id", cert.getCertificateId());
        item.put("create_time", cert.getCreateTime());

        // X.509 解析（失败不阻断列表返回）
        try {
            String pem = decodeBase64Cert(cert.getCertificate());
            CertInfo info = analyzer.analyze(pem);
            item.put("issuer", info.issuer());
            item.put("subject", info.subject());
            item.put("issued_to", info.issuedTo());
            item.put("valid_from", info.validFrom());
            item.put("valid_to", info.validTo());
            item.put("signature_algorithm", info.signatureAlgorithm());
            item.put("public_key_size", info.publicKeySize());
        } catch (Exception e) {
            log.warn("[cert] failed to analyze certificate id={}: {}", cert.getId(), e.getMessage());
        }
        return item;
    }
}
