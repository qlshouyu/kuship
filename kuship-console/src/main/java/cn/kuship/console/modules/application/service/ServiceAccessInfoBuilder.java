package cn.kuship.console.modules.application.service;

import cn.kuship.console.modules.application.entity.ServiceDomain;
import cn.kuship.console.modules.application.entity.ServiceTcpDomain;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.entity.TenantServicesPort;
import cn.kuship.console.modules.application.repository.ServiceDomainRepository;
import cn.kuship.console.modules.application.repository.ServiceTcpDomainRepository;
import cn.kuship.console.modules.application.repository.TenantServicesPortRepository;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 组件访问入口拼装：对齐 rainbond {@code port_service.list_access_infos}（{@code port_service.py:909}）。
 *
 * <p>HTTP 端口走 {@code service_domain} 表渲染 {@code http(s)://domain_name + domain_path}；
 * TCP 端口走 {@code service_tcp_domain} 表的 {@code end_point}，{@code 0.0.0.0} 占位被 region.tcpdomain 替换。
 */
@Service
public class ServiceAccessInfoBuilder {

    private static final String ACCESS_HTTP = "http_port";
    private static final String ACCESS_NOT_HTTP_OUTER = "not_http_outer";
    private static final String ACCESS_NO_PORT = "no_port";

    private final TenantServicesPortRepository portRepo;
    private final ServiceDomainRepository domainRepo;
    private final ServiceTcpDomainRepository tcpDomainRepo;
    private final RegionInfoEntityRepository regionRepo;

    public ServiceAccessInfoBuilder(TenantServicesPortRepository portRepo,
                                       ServiceDomainRepository domainRepo,
                                       ServiceTcpDomainRepository tcpDomainRepo,
                                       RegionInfoEntityRepository regionRepo) {
        this.portRepo = portRepo;
        this.domainRepo = domainRepo;
        this.tcpDomainRepo = tcpDomainRepo;
        this.regionRepo = regionRepo;
    }

    /** 返回每个 service_id 的 {@code {access_type, access_info[]}} 视图（不在端口表里的 service_id 也回填 NO_PORT）。 */
    public Map<String, Map<String, Object>> build(String tenantId, List<TenantService> services) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (services == null || services.isEmpty()) return result;

        List<String> serviceIds = services.stream()
                .map(TenantService::getServiceId).filter(Objects::nonNull).toList();
        List<TenantServicesPort> ports = serviceIds.isEmpty()
                ? List.of() : portRepo.findByTenantIdAndServiceIdIn(tenantId, serviceIds);
        Map<String, List<TenantServicesPort>> httpOuterByService = new HashMap<>();
        Map<String, List<TenantServicesPort>> streamOuterByService = new HashMap<>();
        Map<String, Boolean> hasAnyPort = new HashMap<>();
        for (TenantServicesPort p : ports) {
            hasAnyPort.put(p.getServiceId(), true);
            if ("http".equalsIgnoreCase(p.getProtocol())) {
                if (Boolean.TRUE.equals(p.getOuterService())) {
                    httpOuterByService.computeIfAbsent(p.getServiceId(), k -> new ArrayList<>()).add(p);
                }
            } else if (Boolean.TRUE.equals(p.getOuterService())) {
                streamOuterByService.computeIfAbsent(p.getServiceId(), k -> new ArrayList<>()).add(p);
            }
        }

        // 域名 / TCP 暴露 / region 一次性拉
        List<ServiceDomain> domains = serviceIds.isEmpty()
                ? List.of() : domainRepo.findByServiceIdIn(serviceIds);
        Map<String, List<ServiceDomain>> domainsByService = new HashMap<>();
        for (ServiceDomain d : domains) {
            domainsByService.computeIfAbsent(d.getServiceId(), k -> new ArrayList<>()).add(d);
        }

        List<ServiceTcpDomain> tcpDomains = serviceIds.isEmpty()
                ? List.of() : tcpDomainRepo.findByServiceIdIn(serviceIds);
        Map<String, List<ServiceTcpDomain>> tcpByService = new HashMap<>();
        for (ServiceTcpDomain td : tcpDomains) {
            tcpByService.computeIfAbsent(td.getServiceId(), k -> new ArrayList<>()).add(td);
        }

        Map<String, RegionInfo> regionByName = new HashMap<>();
        for (RegionInfo r : regionRepo.findAll()) {
            if (r.getRegionName() != null) regionByName.put(r.getRegionName(), r);
        }

        for (TenantService svc : services) {
            String sid = svc.getServiceId();
            Map<String, Object> entry = new LinkedHashMap<>();

            if (!Boolean.TRUE.equals(hasAnyPort.get(sid))) {
                entry.put("access_type", ACCESS_NO_PORT);
                entry.put("access_info", List.of());
                result.put(sid, entry);
                continue;
            }

            List<TenantServicesPort> httpPorts = httpOuterByService.getOrDefault(sid, List.of());
            List<TenantServicesPort> streamPorts = streamOuterByService.getOrDefault(sid, List.of());

            if (!httpPorts.isEmpty()) {
                Map<Integer, List<String>> accessUrls = listHttpAccessUrls(domainsByService.getOrDefault(sid, List.of()));
                Map<Integer, List<String>> streamUrls = listStreamUrls(svc, regionByName, tcpByService.getOrDefault(sid, List.of()));
                List<Map<String, Object>> infos = new ArrayList<>();
                String accessType = ACCESS_HTTP;
                for (TenantServicesPort p : httpPorts) {
                    Map<String, Object> dict = portDict(p, svc);
                    if (accessUrls.containsKey(p.getContainerPort())) {
                        dict.put("access_urls", accessUrls.get(p.getContainerPort()));
                        infos.add(dict);
                        continue;
                    }
                    if (streamUrls.containsKey(p.getContainerPort())) {
                        dict.put("access_urls", streamUrls.get(p.getContainerPort()));
                        accessType = ACCESS_NOT_HTTP_OUTER;
                        infos.add(dict);
                    }
                }
                entry.put("access_type", accessType);
                entry.put("access_info", infos);
                result.put(sid, entry);
                continue;
            }

            if (!streamPorts.isEmpty()) {
                Map<Integer, List<String>> streamUrls = listStreamUrls(svc, regionByName, tcpByService.getOrDefault(sid, List.of()));
                List<Map<String, Object>> infos = new ArrayList<>();
                for (TenantServicesPort p : streamPorts) {
                    Map<String, Object> dict = portDict(p, svc);
                    dict.put("access_urls", streamUrls.getOrDefault(p.getContainerPort(), List.of()));
                    infos.add(dict);
                }
                entry.put("access_type", ACCESS_NOT_HTTP_OUTER);
                entry.put("access_info", infos);
                result.put(sid, entry);
                continue;
            }

            entry.put("access_type", ACCESS_NO_PORT);
            entry.put("access_info", List.of());
            result.put(sid, entry);
        }
        return result;
    }

    private static Map<Integer, List<String>> listHttpAccessUrls(List<ServiceDomain> domains) {
        Map<Integer, List<String>> portUrls = new HashMap<>();
        for (ServiceDomain d : domains) {
            if (d.getContainerPort() == null) continue;
            String path = (d.getDomainPath() == null || d.getDomainPath().isBlank()) ? "/" : d.getDomainPath();
            String scheme = "http".equalsIgnoreCase(d.getProtocol()) ? "http" : "https";
            String url = scheme + "://" + d.getDomainName() + path;
            portUrls.computeIfAbsent(d.getContainerPort(), k -> new ArrayList<>()).add(0, url);
        }
        return portUrls;
    }

    private static Map<Integer, List<String>> listStreamUrls(TenantService svc,
                                                                Map<String, RegionInfo> regionByName,
                                                                List<ServiceTcpDomain> tcpDomains) {
        Map<Integer, List<String>> portUrls = new HashMap<>();
        RegionInfo region = svc.getServiceRegion() == null ? null : regionByName.get(svc.getServiceRegion());
        if (region == null) return portUrls;
        for (ServiceTcpDomain td : tcpDomains) {
            if (td.getContainerPort() == null || td.getEndPoint() == null) continue;
            String endpoint = td.getEndPoint();
            if (endpoint.contains("0.0.0.0") && region.getTcpdomain() != null) {
                endpoint = endpoint.replace("0.0.0.0", region.getTcpdomain());
            }
            portUrls.put(td.getContainerPort(), new ArrayList<>(List.of(endpoint)));
        }
        return portUrls;
    }

    /** 把 {@link TenantServicesPort} 拍平为 rainbond {@code port.to_dict()} + service_cname 形状。 */
    private static Map<String, Object> portDict(TenantServicesPort p, TenantService svc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", p.getId());
        m.put("tenant_id", p.getTenantId());
        m.put("service_id", p.getServiceId());
        m.put("container_port", p.getContainerPort());
        m.put("mapping_port", p.getMappingPort());
        m.put("lb_mapping_port", p.getLbMappingPort());
        m.put("protocol", p.getProtocol());
        m.put("port_alias", p.getPortAlias());
        m.put("is_inner_service", Boolean.TRUE.equals(p.getInnerService()));
        m.put("is_outer_service", Boolean.TRUE.equals(p.getOuterService()));
        m.put("k8s_service_name", p.getK8sServiceName());
        m.put("name", p.getName() == null ? "" : p.getName());
        m.put("service_cname", svc.getServiceCname());
        return m;
    }
}
