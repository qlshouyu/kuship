package cn.kuship.console.modules.gateway.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.GatewayOperations;
import cn.kuship.console.modules.application.entity.ServiceTcpDomain;
import cn.kuship.console.modules.application.repository.ServiceTcpDomainRepository;
import cn.kuship.console.common.util.UuidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * TCP 域名绑定 / 更新 / 解绑业务逻辑（对齐 rainbond Python {@code domain_service.py}）。
 */
@Service
public class GatewayTcpDomainService {

    private static final Logger log = LoggerFactory.getLogger(GatewayTcpDomainService.class);

    private final ServiceTcpDomainRepository tcpDomainRepo;
    private final GatewayOperations gatewayOps;

    public GatewayTcpDomainService(ServiceTcpDomainRepository tcpDomainRepo,
                                    GatewayOperations gatewayOps) {
        this.tcpDomainRepo = tcpDomainRepo;
        this.gatewayOps = gatewayOps;
    }

    /**
     * 绑定 TCP 端口（两阶段：本地 INSERT → region bind_tcp_domain）。
     */
    @Transactional
    public ServiceTcpDomain bindTcpDomain(String regionName, String enterpriseId,
                                           String tenantName, String tenantId,
                                           Map<String, Object> reqBody) {
        // 检查端口冲突（端口重复绑定 → 409）
        String endPoint = str(reqBody, "end_point");
        if (endPoint != null) {
            boolean exists = tcpDomainRepo.findAll().stream()
                    .anyMatch(d -> endPoint.equals(d.getEndPoint()));
            if (exists) {
                throw new ServiceHandleException(409, "tcp port already bound", "TCP 端口已绑定");
            }
        }

        // 1. 本地写入
        ServiceTcpDomain domain = buildFromRequest(reqBody, tenantId);
        if (domain.getTcpRuleId() == null) {
            domain.setTcpRuleId(UuidGenerator.makeUuid());
        }
        domain = tcpDomainRepo.save(domain);

        // 2. region call（失败 → 事务回滚）
        Map<String, Object> regionBody = buildRegionBody(domain, reqBody);
        try {
            gatewayOps.bindTcpDomain(regionName, enterpriseId, tenantName, regionBody);
        } catch (Exception e) {
            log.error("[GatewayTcpDomain] bind_tcp_domain to region failed, rolling back. tcpRuleId={}", domain.getTcpRuleId(), e);
            throw e;
        }
        return domain;
    }

    /**
     * 更新 TCP 端口绑定（先 region update → 后本地 UPDATE）。
     */
    @Transactional
    public ServiceTcpDomain updateTcpDomain(String regionName, String enterpriseId,
                                              String tenantName, String tcpRuleId,
                                              Map<String, Object> reqBody) {
        ServiceTcpDomain domain = tcpDomainRepo.findByTcpRuleId(tcpRuleId)
                .orElseThrow(() -> new ServiceHandleException(404, "tcp rule not found", "TCP 规则不存在"));

        Map<String, Object> regionBody = buildRegionBody(domain, reqBody);
        regionBody.put("tcp_rule_id", tcpRuleId);
        gatewayOps.updateTcpDomain(regionName, enterpriseId, tenantName, regionBody);

        applyUpdates(domain, reqBody);
        return tcpDomainRepo.save(domain);
    }

    /**
     * 解绑 TCP 端口（先 region unbind_tcp_domain → 后本地 DELETE）。
     */
    public void unbindTcpDomain(String regionName, String enterpriseId,
                                 String tenantName, String tcpRuleId) {
        ServiceTcpDomain domain = tcpDomainRepo.findByTcpRuleId(tcpRuleId)
                .orElseThrow(() -> new ServiceHandleException(404, "tcp rule not found", "TCP 规则不存在"));

        Map<String, Object> regionBody = Map.of("tcp_rule_id", tcpRuleId);
        gatewayOps.unbindTcpDomain(regionName, enterpriseId, tenantName, regionBody);

        tcpDomainRepo.deleteById(domain.getId());
    }

    // ─── 工具方法 ──────────────────────────────────────────────────────────────

    private ServiceTcpDomain buildFromRequest(Map<String, Object> body, String tenantId) {
        ServiceTcpDomain d = new ServiceTcpDomain();
        d.setTenantId(tenantId);
        d.setServiceId(str(body, "service_id"));
        d.setServiceAlias(str(body, "service_alias"));
        d.setServiceName(str(body, "service_name"));
        d.setContainerPort(intVal(body, "container_port"));
        d.setEndPoint(str(body, "end_point"));
        d.setOuterService(true);
        d.setProtocol(str(body, "protocol", "tcp"));
        d.setType(intVal(body, "type", 0));
        d.setRuleExtensions(str(body, "rule_extensions"));
        d.setTcpRuleId(str(body, "tcp_rule_id"));
        d.setRegionId(str(body, "region_id"));
        return d;
    }

    private void applyUpdates(ServiceTcpDomain d, Map<String, Object> body) {
        if (body.containsKey("end_point")) d.setEndPoint(str(body, "end_point"));
        if (body.containsKey("protocol")) d.setProtocol(str(body, "protocol"));
        if (body.containsKey("type")) d.setType(intVal(body, "type"));
        if (body.containsKey("rule_extensions")) d.setRuleExtensions(str(body, "rule_extensions"));
    }

    private Map<String, Object> buildRegionBody(ServiceTcpDomain d, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>(extra != null ? extra : Map.of());
        body.put("tcp_rule_id", d.getTcpRuleId());
        body.put("service_id", d.getServiceId());
        body.put("tenant_id", d.getTenantId());
        body.put("container_port", d.getContainerPort());
        body.put("end_point", d.getEndPoint());
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
