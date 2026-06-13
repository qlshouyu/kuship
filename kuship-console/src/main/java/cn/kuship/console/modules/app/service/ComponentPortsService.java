package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import cn.kuship.console.modules.app.entity.TenantServiceEnvVar;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.entity.TenantServicesPort;
import cn.kuship.console.modules.app.repository.TenantServiceEnvVarRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.app.repository.TenantServicesPortRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 组件端口列表读（对齐 AppPortView.get）。每端口 = port.to_dict + service_alias + environment(inner) +
 * inner_url + outer_url(outer 时 tcpdomain/httpdomain) + bind_domains/bind_tcp_domains(region 网关)。
 * 读路径：不执行 rainbond 的 port.save() 副作用（is_outer_service 显示值在内存按网关结果计算）。
 */
@Service
public class ComponentPortsService {

    private static final Logger log = LoggerFactory.getLogger(ComponentPortsService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TenantServiceInfoRepository serviceRepository;
    private final TenantsRepository tenantsRepository;
    private final TenantServicesPortRepository portRepository;
    private final TenantServiceEnvVarRepository envRepository;
    private final RegionConfigRepository regionConfigRepository;
    private final RegionClient regionClient;

    public ComponentPortsService(TenantServiceInfoRepository serviceRepository,
                                 TenantsRepository tenantsRepository,
                                 TenantServicesPortRepository portRepository,
                                 TenantServiceEnvVarRepository envRepository,
                                 RegionConfigRepository regionConfigRepository,
                                 RegionClient regionClient) {
        this.serviceRepository = serviceRepository;
        this.tenantsRepository = tenantsRepository;
        this.portRepository = portRepository;
        this.envRepository = envRepository;
        this.regionConfigRepository = regionConfigRepository;
        this.regionClient = regionClient;
    }

    public List<Map<String, Object>> listPorts(String tenantName, String serviceAlias, String regionName) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        Tenants tenant = tenantsRepository.findByTenantName(tenantName)
                .orElseThrow(() -> ServiceHandleException.notFound("team not found", "团队不存在"));
        String region = (regionName == null || regionName.isBlank()) ? service.getServiceRegion() : regionName;
        RegionConfig regionConfig = regionConfigRepository.findByRegionName(region).orElse(null);

        List<TenantServicesPort> ports = portRepository
                .findByTenantIdAndServiceIdOrderById(service.getTenantId(), service.getServiceId());
        List<Map<String, Object>> portList = new ArrayList<>();
        for (TenantServicesPort port : ports) {
            Map<String, Object> info = port.toDict();
            info.put("service_alias", serviceAlias);

            // environment（仅 inner service）
            List<Map<String, Object>> environment = new ArrayList<>();
            if (port.isInnerService()) {
                for (TenantServiceEnvVar env : envRepository.findByTenantIdAndServiceIdAndContainerPortOrderById(
                        service.getTenantId(), service.getServiceId(), port.getContainerPort())) {
                    Map<String, Object> v = new java.util.LinkedHashMap<>();
                    v.put("desc", env.getName());
                    v.put("name", env.getAttrName());
                    v.put("value", env.getAttrValue());
                    environment.add(v);
                }
            }
            info.put("environment", environment);

            // inner_url
            String innerUrl = "";
            if (!environment.isEmpty() && port.isInnerService()) {
                String innerHost = "127.0.0.1";
                String innerPort = null;
                for (Map<String, Object> pf : environment) {
                    Object nm = pf.get("name");
                    if (nm == null) {
                        continue;
                    }
                    String n = nm.toString();
                    if (n.endsWith("PORT")) {
                        innerPort = String.valueOf(pf.get("value"));
                    }
                    if (n.endsWith("HOST")) {
                        innerHost = String.valueOf(pf.get("value"));
                    }
                }
                innerUrl = innerHost + ":" + innerPort;
            }
            info.put("inner_url", innerUrl);

            // outer_url（outer service：tcp→tcpdomain:mapping/lb；http→httpdomain 复合）
            info.put("outer_url", outerUrl(port, service, tenant, regionConfig));

            // bind_domains / bind_tcp_domains（region 网关）
            info.put("bind_domains", new ArrayList<>());
            if ("http".equals(port.getProtocol())) {
                List<Object> domains = gatewayList(region, tenant, serviceAlias, port.getContainerPort(), "http");
                if (!domains.isEmpty()) {
                    List<Map<String, Object>> bind = new ArrayList<>();
                    for (Object host : domains) {
                        Map<String, Object> bd = new java.util.LinkedHashMap<>();
                        bd.put("protocol", "http");
                        bd.put("domain_type", "www");
                        bd.put("ID", -1);
                        bd.put("domain_name", host);
                        bd.put("container_port", port.getContainerPort());
                        bind.add(bd);
                    }
                    info.put("bind_domains", bind);
                    info.put("is_outer_service", true);
                } else {
                    info.put("is_outer_service", false);
                }
            } else {
                List<Object> nodeports = gatewayList(region, tenant, serviceAlias, port.getContainerPort(), "tcp");
                List<Map<String, Object>> tcpList = new ArrayList<>();
                for (Object nodeport : nodeports) {
                    Map<String, Object> td = new java.util.LinkedHashMap<>();
                    td.put("protocol", port.getProtocol());
                    td.put("domain_name", "0.0.0.0:" + nodeport);
                    td.put("container_port", port.getContainerPort());
                    td.put("service_id", service.getServiceId());
                    td.put("service_name", serviceAlias);
                    td.put("service_alias", serviceAlias);
                    td.put("is_outer_service", true);
                    td.put("end_point", "0.0.0.0:" + nodeport);
                    tcpList.add(td);
                }
                info.put("bind_tcp_domains", tcpList);
                info.put("is_outer_service", !tcpList.isEmpty());
            }
            portList.add(info);
        }
        return portList;
    }

    /** 对齐 get_port_variables 的 outer_service 分支（按 DB is_outer_service）。 */
    private String outerUrl(TenantServicesPort port, TenantServiceInfo service, Tenants tenant, RegionConfig rc) {
        if (!port.isOuterService() || rc == null) {
            return "";
        }
        String protocol = port.getProtocol();
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            String domain = rc.getTcpdomain();
            Object p = (port.getLbMappingPort() != null && port.getLbMappingPort() != 0)
                    ? port.getLbMappingPort() : port.getMappingPort();
            return domain + ":" + p;
        } else if ("http".equals(protocol)) {
            String httpdomain = rc.getHttpdomain();
            String domain = httpdomain;
            int p = 80;
            if (httpdomain != null && httpdomain.contains(":")) {
                String[] info = httpdomain.split(":", 2);
                if (info.length == 2) {
                    p = Integer.parseInt(info[1]);
                    domain = info[0];
                }
            }
            String composite = port.getContainerPort() + "." + service.getServiceAlias() + "."
                    + tenant.getTenantName() + "." + domain;
            return composite + ":" + p;
        }
        return "";
    }

    /** region 网关 GET routes/{kind}/domains?service_alias=&port= → body.list（异常/空→[]）。 */
    @SuppressWarnings("unchecked")
    private List<Object> gatewayList(String region, Tenants tenant, String serviceAlias, Integer containerPort, String kind) {
        String path = "/api-gateway/v1/" + tenant.getTenantName() + "/routes/" + kind + "/domains?service_alias="
                + serviceAlias + "&port=" + containerPort;
        try {
            String body = regionClient.exchange(region, "GET", path, null, TimeoutTier.NORMAL, null);
            Object list = MAPPER.readValue(body, Map.class).get("list");
            return list == null ? List.of() : (List<Object>) list;
        } catch (Exception e) {
            log.warn("api gateway {} domains failed: {}/{}", kind, serviceAlias, containerPort, e);
            return List.of();
        }
    }
}
