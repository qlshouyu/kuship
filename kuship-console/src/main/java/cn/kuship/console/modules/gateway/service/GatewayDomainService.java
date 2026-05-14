package cn.kuship.console.modules.gateway.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.GatewayOperations;
import cn.kuship.console.modules.application.entity.ServiceDomain;
import cn.kuship.console.modules.application.repository.ServiceDomainRepository;
import cn.kuship.console.common.util.UuidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 域名绑定 / 更新 / 解绑业务逻辑（对齐 rainbond Python {@code domain_service.py}）。
 *
 * <p>两阶段写策略（遵循 design.md 决策 3）：
 * <ul>
 *   <li>bind：事务内 INSERT → region call；region 失败 → 事务回滚（本地行撤销）</li>
 *   <li>unbind：先 region call（释放 ingress）→ 成功后 DELETE 本地行；region 失败抛异常</li>
 *   <li>update：先 region call（upsert ingress）→ 成功后 UPDATE 本地行；region 失败抛异常</li>
 * </ul>
 */
@Service
public class GatewayDomainService {

    private static final Logger log = LoggerFactory.getLogger(GatewayDomainService.class);

    private final ServiceDomainRepository domainRepo;
    private final GatewayOperations gatewayOps;

    public GatewayDomainService(ServiceDomainRepository domainRepo,
                                 GatewayOperations gatewayOps) {
        this.domainRepo = domainRepo;
        this.gatewayOps = gatewayOps;
    }

    /**
     * 绑定 HTTP 域名（两阶段：本地 INSERT → region bind_http_domain）。
     */
    @Transactional
    public ServiceDomain bindHttpDomain(String regionName, String enterpriseId,
                                         String tenantName, String tenantId,
                                         Map<String, Object> reqBody) {
        // 检查域名冲突
        String domainName = (String) reqBody.get("domain_name");
        if (domainName != null) {
            boolean exists = domainRepo.findAll().stream()
                    .anyMatch(d -> domainName.equals(d.getDomainName()));
            if (exists) {
                throw new ServiceHandleException(409, "domain already exists", "域名已存在");
            }
        }

        // 1. 生成 http_rule_id 并本地写入
        ServiceDomain domain = buildDomainFromRequest(reqBody, tenantId);
        if (domain.getHttpRuleId() == null) {
            domain.setHttpRuleId(UuidGenerator.makeUuid());
        }
        domain = domainRepo.save(domain);

        // 2. 调 region（@Transactional 包裹，region 失败 → 事务回滚）
        Map<String, Object> regionBody = buildRegionHttpBody(domain, reqBody);
        try {
            gatewayOps.bindHttpDomain(regionName, enterpriseId, tenantName, regionBody);
        } catch (Exception e) {
            log.error("[GatewayDomain] bind_http_domain to region failed, rolling back local insert. httpRuleId={}", domain.getHttpRuleId(), e);
            throw e; // 事务回滚
        }
        return domain;
    }

    /**
     * 解绑 HTTP 域名（先 region delete_http_domain → 后本地 DELETE）。
     */
    public void unbindHttpDomain(String regionName, String enterpriseId,
                                  String tenantName, String httpRuleId) {
        ServiceDomain domain = domainRepo.findByHttpRuleId(httpRuleId)
                .orElseThrow(() -> new ServiceHandleException(404, "domain rule not found", "域名规则不存在"));

        // 1. 先调 region 释放 ingress
        Map<String, Object> regionBody = Map.of("http_rule_id", httpRuleId);
        gatewayOps.deleteHttpDomain(regionName, enterpriseId, tenantName, regionBody);

        // 2. region 成功后删本地行
        domainRepo.deleteById(domain.getId());
    }

    /**
     * 更新 HTTP 域名规则（先 region update → 后本地 UPDATE）。
     */
    @Transactional
    public ServiceDomain updateHttpDomain(String regionName, String enterpriseId,
                                           String tenantName, String httpRuleId,
                                           Map<String, Object> reqBody) {
        ServiceDomain domain = domainRepo.findByHttpRuleId(httpRuleId)
                .orElseThrow(() -> new ServiceHandleException(404, "domain rule not found", "域名规则不存在"));

        // 1. 调 region upsert
        Map<String, Object> regionBody = buildRegionHttpBody(domain, reqBody);
        regionBody.put("http_rule_id", httpRuleId);
        gatewayOps.updateHttpDomain(regionName, enterpriseId, tenantName, regionBody);

        // 2. 更新本地字段
        applyUpdates(domain, reqBody);
        return domainRepo.save(domain);
    }

    /**
     * 检查域名是否存在。
     */
    public boolean checkDomainExist(String domainName, Integer containerPort) {
        return domainRepo.findAll().stream()
                .anyMatch(d -> domainName.equals(d.getDomainName())
                        && (containerPort == null || containerPort.equals(d.getContainerPort())));
    }

    // ─── 工具方法 ──────────────────────────────────────────────────────────────

    private ServiceDomain buildDomainFromRequest(Map<String, Object> body, String tenantId) {
        ServiceDomain d = new ServiceDomain();
        d.setTenantId(tenantId);
        d.setServiceId(str(body, "service_id"));
        d.setServiceAlias(str(body, "service_alias"));
        d.setServiceName(str(body, "service_name"));
        d.setContainerPort(intVal(body, "container_port"));
        d.setDomainName(str(body, "domain_name"));
        d.setProtocol(str(body, "protocol", "http"));
        d.setDomainPath(str(body, "domain_path"));
        d.setDomainCookie(str(body, "domain_cookie"));
        d.setDomainHeander(str(body, "domain_heander"));
        d.setCertificateId(intVal(body, "certificate_id"));
        d.setDomainType(str(body, "domain_type", "www"));
        d.setIsSenior(intVal(body, "is_senior", 0) != 0);
        d.setType(intVal(body, "type", 0));
        d.setTheWeight(intVal(body, "the_weight", 100));
        d.setRuleExtensions(str(body, "rule_extensions"));
        d.setIsOuterService(intVal(body, "is_outer_service", 1) != 0);
        d.setAutoSsl(intVal(body, "auto_ssl", 0) != 0);
        d.setAutoSslConfig(str(body, "auto_ssl_config"));
        d.setPathRewrite(intVal(body, "path_rewrite", 0) != 0);
        d.setRewrites(str(body, "rewrites"));
        d.setHttpRuleId(str(body, "http_rule_id"));
        d.setRegionId(str(body, "region_id"));
        return d;
    }

    private void applyUpdates(ServiceDomain d, Map<String, Object> body) {
        if (body.containsKey("domain_name")) d.setDomainName(str(body, "domain_name"));
        if (body.containsKey("domain_path")) d.setDomainPath(str(body, "domain_path"));
        if (body.containsKey("domain_cookie")) d.setDomainCookie(str(body, "domain_cookie"));
        if (body.containsKey("domain_heander")) d.setDomainHeander(str(body, "domain_heander"));
        if (body.containsKey("certificate_id")) d.setCertificateId(intVal(body, "certificate_id"));
        if (body.containsKey("the_weight")) d.setTheWeight(intVal(body, "the_weight"));
        if (body.containsKey("rule_extensions")) d.setRuleExtensions(str(body, "rule_extensions"));
        if (body.containsKey("is_senior")) d.setIsSenior(intVal(body, "is_senior") != 0);
        if (body.containsKey("path_rewrite")) d.setPathRewrite(intVal(body, "path_rewrite") != 0);
        if (body.containsKey("rewrites")) d.setRewrites(str(body, "rewrites"));
        if (body.containsKey("auto_ssl")) d.setAutoSsl(intVal(body, "auto_ssl") != 0);
        if (body.containsKey("auto_ssl_config")) d.setAutoSslConfig(str(body, "auto_ssl_config"));
    }

    private Map<String, Object> buildRegionHttpBody(ServiceDomain d, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>(extra != null ? extra : Map.of());
        body.put("http_rule_id", d.getHttpRuleId());
        body.put("service_id", d.getServiceId());
        body.put("tenant_id", d.getTenantId());
        body.put("container_port", d.getContainerPort());
        body.put("domain_name", d.getDomainName());
        body.put("protocol", d.getProtocol());
        return body;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    private static String str(Map<String, Object> m, String k, String def) {
        String v = str(m, k);
        return v == null ? def : v;
    }

    private static Integer intVal(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer intVal(Map<String, Object> m, String k, int def) {
        Integer v = intVal(m, k);
        return v == null ? def : v;
    }
}
