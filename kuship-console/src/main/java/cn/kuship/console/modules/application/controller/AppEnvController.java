package cn.kuship.console.modules.application.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.dto.EnvReq;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.entity.TenantServiceEnvVar;
import cn.kuship.console.modules.application.repository.TenantServiceEnvVarRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /console/teams/{team_name}/apps/{service_alias}/envs}：环境变量 CRUD（仅本地写）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}/envs")
public class AppEnvController {

    private final TenantServiceEnvVarRepository envRepo;
    private final TenantServiceRepository serviceRepo;

    public AppEnvController(TenantServiceEnvVarRepository envRepo, TenantServiceRepository serviceRepo) {
        this.envRepo = envRepo;
        this.serviceRepo = serviceRepo;
    }

    @GetMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_ENV)
    public ApiResult list(@PathVariable("team_name") String teamName,
                            @PathVariable("service_alias") String serviceAlias,
                            @RequestParam(value = "scope", required = false) String scope) {
        TenantService s = requireService(serviceAlias);
        List<TenantServiceEnvVar> rows = (scope == null || scope.isBlank())
                ? envRepo.findByServiceId(s.getServiceId())
                : envRepo.findByServiceIdAndScope(s.getServiceId(), scope);
        return GeneralMessage.okList(rows.stream().map(this::serialize).toList());
    }

    @PostMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_ENV)
    @Transactional
    public ApiResult create(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @RequestBody @Valid EnvReq req) {
        TenantService s = requireService(serviceAlias);
        String scope = req.scope() == null ? "outer" : req.scope();
        if (envRepo.findByServiceIdAndAttrNameAndScope(s.getServiceId(), req.attrName(), scope).isPresent()) {
            throw new ServiceHandleException(400, "env name already exists", "环境变量名已存在");
        }
        TenantServiceEnvVar e = new TenantServiceEnvVar();
        e.setTenantId(s.getTenantId());
        e.setServiceId(s.getServiceId());
        e.setName(req.name() != null ? req.name() : req.attrName());
        e.setAttrName(req.attrName());
        e.setAttrValue(req.attrValue() != null ? req.attrValue() : "");
        e.setChange(req.change() != null ? req.change() : true);
        e.setScope(scope);
        e.setContainerPort(req.containerPort() != null ? req.containerPort() : 0);
        e.setCreateTime(LocalDateTime.now());
        return GeneralMessage.ok(serialize(envRepo.save(e)));
    }

    @PutMapping(value = {"/{env_id}", "/{env_id}/"})
    @RequirePerm(PermCode.APP_OVERVIEW_ENV)
    @Transactional
    public ApiResult update(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @PathVariable("env_id") Integer envId,
                              @RequestBody @Valid EnvReq req) {
        TenantServiceEnvVar e = envRepo.findById(envId)
                .orElseThrow(() -> new ServiceHandleException(404, "env not found", "环境变量不存在"));
        if (req.name() != null) e.setName(req.name());
        if (req.attrValue() != null) e.setAttrValue(req.attrValue());
        if (req.change() != null) e.setChange(req.change());
        return GeneralMessage.ok(serialize(envRepo.save(e)));
    }

    @DeleteMapping(value = {"/{env_id}", "/{env_id}/"})
    @RequirePerm(PermCode.APP_OVERVIEW_ENV)
    @Transactional
    public ApiResult delete(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @PathVariable("env_id") Integer envId) {
        envRepo.deleteById(envId);
        return GeneralMessage.ok();
    }

    private TenantService requireService(String serviceAlias) {
        return serviceRepo.findAll().stream()
                .filter(s -> serviceAlias.equals(s.getServiceAlias()))
                .findFirst()
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    private Map<String, Object> serialize(TenantServiceEnvVar e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("attr_name", e.getAttrName());
        m.put("attr_value", e.getAttrValue());
        m.put("is_change", e.getChange());
        m.put("scope", e.getScope());
        m.put("container_port", e.getContainerPort());
        m.put("create_time", e.getCreateTime());
        return m;
    }
}
