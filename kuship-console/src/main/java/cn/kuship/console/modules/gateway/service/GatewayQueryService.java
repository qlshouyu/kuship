package cn.kuship.console.modules.gateway.service;

import cn.kuship.console.common.page.PageRequestAdapter;
import cn.kuship.console.modules.application.entity.ServiceDomain;
import cn.kuship.console.modules.application.entity.ServiceTcpDomain;
import cn.kuship.console.modules.application.repository.ServiceDomainRepository;
import cn.kuship.console.modules.application.repository.ServiceTcpDomainRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 网关域名列表查询（HTTP + TCP），对齐 rainbond Python {@code domain_service.py:DomainQueryView}。
 */
@Service
public class GatewayQueryService {

    private final ServiceDomainRepository domainRepo;
    private final ServiceTcpDomainRepository tcpDomainRepo;
    private final PageRequestAdapter pageAdapter;

    public GatewayQueryService(ServiceDomainRepository domainRepo,
                                ServiceTcpDomainRepository tcpDomainRepo,
                                PageRequestAdapter pageAdapter) {
        this.domainRepo = domainRepo;
        this.tcpDomainRepo = tcpDomainRepo;
        this.pageAdapter = pageAdapter;
    }

    /**
     * 团队维度 HTTP 域名列表（分页 + 搜索）。
     */
    public Page<ServiceDomain> getTeamHttpDomains(String tenantId, String search,
                                                    int page, int pageSize) {
        Pageable pageable = pageAdapter.toPageable(page, pageSize);
        return domainRepo.findByTenantIdWithSearch(tenantId, search, pageable);
    }

    /**
     * 应用维度 HTTP 域名列表（分页 + 搜索）。
     */
    public Page<ServiceDomain> getAppHttpDomains(List<String> serviceIds, String search,
                                                   int page, int pageSize) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return Page.empty(pageAdapter.toPageable(page, pageSize));
        }
        Pageable pageable = pageAdapter.toPageable(page, pageSize);
        return domainRepo.findByServiceIdsWithSearch(serviceIds, search, pageable);
    }

    /**
     * 团队维度 TCP 域名列表（分页 + 搜索）。
     */
    public Page<ServiceTcpDomain> getTeamTcpDomains(String tenantId, String search,
                                                      int page, int pageSize) {
        Pageable pageable = pageAdapter.toPageable(page, pageSize);
        return tcpDomainRepo.findByTenantIdWithSearch(tenantId, search, pageable);
    }

    /**
     * 应用维度 TCP 域名列表（分页）。
     */
    public Page<ServiceTcpDomain> getAppTcpDomains(List<String> serviceIds,
                                                     int page, int pageSize) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return Page.empty(pageAdapter.toPageable(page, pageSize));
        }
        Pageable pageable = pageAdapter.toPageable(page, pageSize);
        return tcpDomainRepo.findByServiceIds(serviceIds, pageable);
    }

    /**
     * 按组件端口查 HTTP 域名规则列表。
     */
    public List<ServiceDomain> getPortHttpRules(String serviceId, Integer containerPort) {
        return domainRepo.findByServiceIdAndContainerPort(serviceId, containerPort);
    }

    /**
     * 按组件端口查 TCP 域名规则列表。
     */
    public List<ServiceTcpDomain> getPortTcpRules(String serviceId, Integer containerPort) {
        return tcpDomainRepo.findByServiceIdAndContainerPort(serviceId, containerPort);
    }
}
