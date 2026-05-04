package cn.kuship.console.modules.application.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServicePortOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.dto.PortReq;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.entity.TenantServicesPort;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.application.repository.TenantServicesPortRepository;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code /console/teams/{team_name}/apps/{service_alias}/ports}：先 region 后本地。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}/ports")
public class AppPortController {

    private final TenantServicesPortRepository portRepo;
    private final TenantServiceRepository serviceRepo;
    private final TenantsRepository tenantsRepo;
    private final ServicePortOperations portOperations;

    public AppPortController(TenantServicesPortRepository portRepo,
                              TenantServiceRepository serviceRepo,
                              TenantsRepository tenantsRepo,
                              ServicePortOperations portOperations) {
        this.portRepo = portRepo;
        this.serviceRepo = serviceRepo;
        this.tenantsRepo = tenantsRepo;
        this.portOperations = portOperations;
    }

    @GetMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult list(@PathVariable("team_name") String teamName,
                            @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(serviceAlias);
        return GeneralMessage.okList(portRepo.findByServiceId(s.getServiceId()).stream()
                .map(this::serialize).toList());
    }

    @PostMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult add(@PathVariable("team_name") String teamName,
                           @PathVariable("service_alias") String serviceAlias,
                           @RequestBody @Valid PortReq req) {
        Ctx ctx = require(teamName, serviceAlias);
        if (portRepo.findByServiceIdAndContainerPort(ctx.service.getServiceId(), req.port()).isPresent()) {
            throw new ServiceHandleException(400, "port already exists", "端口已存在");
        }
        Map<String, Object> body = toBody(req, ctx);
        portOperations.addPort(ctx.service.getServiceRegion(), ctx.team.getTenantName(), serviceAlias, body);
        TenantServicesPort p = new TenantServicesPort();
        p.setTenantId(ctx.service.getTenantId());
        p.setServiceId(ctx.service.getServiceId());
        p.setContainerPort(req.port());
        p.setProtocol(req.protocol() != null ? req.protocol() : "tcp");
        p.setPortAlias(req.portAlias());
        p.setInnerService(req.innerService() != null && req.innerService());
        p.setOuterService(req.outerService() != null && req.outerService());
        p.setK8sServiceName(req.k8sServiceName());
        p.setName(req.name());
        p.setMappingPort(req.port());
        p.setLbMappingPort(0);
        return GeneralMessage.ok(serialize(portRepo.save(p)));
    }

    @DeleteMapping(value = {"/{port}", "/{port}/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult delete(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @PathVariable("port") Integer port) {
        Ctx ctx = require(teamName, serviceAlias);
        TenantServicesPort p = portRepo.findByServiceIdAndContainerPort(ctx.service.getServiceId(), port)
                .orElseThrow(() -> new ServiceHandleException(404, "port not found", "端口不存在"));
        portOperations.deletePort(ctx.service.getServiceRegion(), ctx.team.getTenantName(),
                serviceAlias, port, ctx.team.getEnterpriseId(), Map.of());
        portRepo.delete(p);
        return GeneralMessage.ok();
    }

    @PutMapping(value = {"/{port}", "/{port}/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult update(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @PathVariable("port") Integer port,
                              @RequestBody @Valid PortReq req) {
        Ctx ctx = require(teamName, serviceAlias);
        TenantServicesPort p = portRepo.findByServiceIdAndContainerPort(ctx.service.getServiceId(), port)
                .orElseThrow(() -> new ServiceHandleException(404, "port not found", "端口不存在"));
        // 切换 inner / outer 时调对应 region method
        if (req.innerService() != null && !req.innerService().equals(p.getInnerService())) {
            portOperations.manageInnerPort(ctx.service.getServiceRegion(), ctx.team.getTenantName(),
                    serviceAlias, port, Map.of("operation", req.innerService() ? "open" : "close"));
            p.setInnerService(req.innerService());
        }
        if (req.outerService() != null && !req.outerService().equals(p.getOuterService())) {
            portOperations.manageOuterPort(ctx.service.getServiceRegion(), ctx.team.getTenantName(),
                    serviceAlias, port, Map.of("operation", req.outerService() ? "open" : "close"));
            p.setOuterService(req.outerService());
        }
        if (req.portAlias() != null) p.setPortAlias(req.portAlias());
        if (req.k8sServiceName() != null) p.setK8sServiceName(req.k8sServiceName());
        return GeneralMessage.ok(serialize(portRepo.save(p)));
    }

    private Ctx require(String teamName, String serviceAlias) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        TenantService service = requireService(serviceAlias);
        return new Ctx(team, service);
    }

    private TenantService requireService(String serviceAlias) {
        return serviceRepo.findAll().stream()
                .filter(s -> serviceAlias.equals(s.getServiceAlias()))
                .findFirst()
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    private Map<String, Object> toBody(PortReq req, Ctx ctx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("port", req.port());
        m.put("protocol", req.protocol() != null ? req.protocol() : "tcp");
        m.put("port_alias", req.portAlias());
        m.put("is_inner_service", req.innerService() != null && req.innerService());
        m.put("is_outer_service", req.outerService() != null && req.outerService());
        m.put("k8s_service_name", req.k8sServiceName());
        return m;
    }

    private Map<String, Object> serialize(TenantServicesPort p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("port_id", p.getId());
        m.put("container_port", p.getContainerPort());
        m.put("mapping_port", p.getMappingPort());
        m.put("protocol", p.getProtocol());
        m.put("port_alias", p.getPortAlias());
        m.put("is_inner_service", p.getInnerService());
        m.put("is_outer_service", p.getOuterService());
        m.put("k8s_service_name", p.getK8sServiceName());
        return m;
    }

    private record Ctx(Tenants team, TenantService service) {}
}
