package cn.kuship.console.modules.application.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.infrastructure.region.api.ServiceProbeOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.dto.ProbeReq;
import cn.kuship.console.modules.application.entity.ServiceProbe;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceProbeRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code .../probe}：探针管理（同 mode 软去重）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}/probe")
public class AppProbeController {

    private final ServiceProbeRepository probeRepo;
    private final TenantServiceRepository serviceRepo;
    private final ServiceProbeOperations probeOperations;

    public AppProbeController(ServiceProbeRepository probeRepo,
                                TenantServiceRepository serviceRepo,
                                ServiceProbeOperations probeOperations) {
        this.probeRepo = probeRepo;
        this.serviceRepo = serviceRepo;
        this.probeOperations = probeOperations;
    }

    @GetMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult list(@PathVariable("team_name") String teamName,
                            @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(serviceAlias);
        return GeneralMessage.okList(probeRepo.findByServiceId(s.getServiceId()).stream()
                .map(this::serialize).toList());
    }

    @PostMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult upsert(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @RequestBody @Valid ProbeReq req) {
        TenantService s = requireService(serviceAlias);
        Map<String, Object> body = toBody(req);
        // 软去重：先 delete 同 service_id+mode → 再 insert
        boolean exist = !probeRepo.findByServiceIdAndMode(s.getServiceId(), req.mode()).isEmpty();
        if (exist) {
            probeOperations.deleteProbe(s.getServiceRegion(), teamName, serviceAlias, body);
            probeRepo.deleteByServiceIdAndMode(s.getServiceId(), req.mode());
        }
        probeOperations.addProbe(s.getServiceRegion(), teamName, serviceAlias, body);
        ServiceProbe p = new ServiceProbe();
        p.setServiceId(s.getServiceId());
        p.setProbeId(UuidGenerator.makeUuid());
        p.setMode(req.mode());
        p.setScheme(req.scheme() != null ? req.scheme() : "tcp");
        p.setPath(req.path() != null ? req.path() : "");
        p.setPort(req.port() != null ? req.port() : 0);
        p.setCmd(req.cmd() != null ? req.cmd() : "");
        p.setHttpHeader(req.httpHeader() != null ? req.httpHeader() : "");
        p.setInitialDelaySecond(req.initialDelaySecond() != null ? req.initialDelaySecond() : 4);
        p.setPeriodSecond(req.periodSecond() != null ? req.periodSecond() : 5);
        p.setTimeoutSecond(req.timeoutSecond() != null ? req.timeoutSecond() : 30);
        p.setFailureThreshold(req.failureThreshold() != null ? req.failureThreshold() : 3);
        p.setSuccessThreshold(req.successThreshold() != null ? req.successThreshold() : 1);
        p.setUsed(req.used() != null ? req.used() : true);
        return GeneralMessage.ok(serialize(probeRepo.save(p)));
    }

    @DeleteMapping(value = {"/{probe_id}", "/{probe_id}/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult delete(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @PathVariable("probe_id") Integer probeId) {
        TenantService s = requireService(serviceAlias);
        ServiceProbe p = probeRepo.findById(probeId)
                .orElseThrow(() -> new ServiceHandleException(404, "probe not found", "探针不存在"));
        Map<String, Object> body = Map.of("probe_id", p.getProbeId(), "mode", p.getMode());
        probeOperations.deleteProbe(s.getServiceRegion(), teamName, serviceAlias, body);
        probeRepo.delete(p);
        return GeneralMessage.ok();
    }

    private TenantService requireService(String serviceAlias) {
        return serviceRepo.findAll().stream()
                .filter(s -> serviceAlias.equals(s.getServiceAlias()))
                .findFirst()
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    private Map<String, Object> toBody(ProbeReq req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", req.mode());
        m.put("scheme", req.scheme());
        m.put("path", req.path());
        m.put("port", req.port());
        m.put("cmd", req.cmd());
        m.put("http_header", req.httpHeader());
        m.put("initial_delay_second", req.initialDelaySecond());
        m.put("period_second", req.periodSecond());
        m.put("timeout_second", req.timeoutSecond());
        m.put("failure_threshold", req.failureThreshold());
        m.put("success_threshold", req.successThreshold());
        return m;
    }

    private Map<String, Object> serialize(ServiceProbe p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("probe_id", p.getId());
        m.put("uuid", p.getProbeId());
        m.put("mode", p.getMode());
        m.put("scheme", p.getScheme());
        m.put("path", p.getPath());
        m.put("port", p.getPort());
        m.put("cmd", p.getCmd());
        m.put("initial_delay_second", p.getInitialDelaySecond());
        m.put("period_second", p.getPeriodSecond());
        m.put("timeout_second", p.getTimeoutSecond());
        m.put("failure_threshold", p.getFailureThreshold());
        m.put("success_threshold", p.getSuccessThreshold());
        m.put("is_used", p.getUsed());
        return m;
    }
}
