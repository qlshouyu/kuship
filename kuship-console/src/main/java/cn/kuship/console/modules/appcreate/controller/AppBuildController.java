package cn.kuship.console.modules.appcreate.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.entity.TenantServiceEnvVar;
import cn.kuship.console.modules.application.repository.TenantServiceEnvVarRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code .../apps/{service_alias}/{build, code/branch, compile_env}}：构建 + 编译参数。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class AppBuildController {

    private final TenantServiceRepository serviceRepo;
    private final TenantServiceEnvVarRepository envRepo;
    private final ServiceOperations serviceOperations;
    private final RequestContext requestContext;

    public AppBuildController(TenantServiceRepository serviceRepo,
                                TenantServiceEnvVarRepository envRepo,
                                ServiceOperations serviceOperations,
                                RequestContext requestContext) {
        this.serviceRepo = serviceRepo;
        this.envRepo = envRepo;
        this.serviceOperations = serviceOperations;
        this.requestContext = requestContext;
    }

    @PostMapping(value = {"/build", "/build/"})
    @RequirePerm(PermCode.APP_OVERVIEW_CREATE)
    @Transactional
    public ApiResult build(@PathVariable("team_name") String teamName,
                             @PathVariable("service_alias") String serviceAlias,
                             @RequestBody(required = false) Map<String, Object> body) {
        TenantService s = requireService(serviceAlias);
        Map<String, Object> req = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
        req.putIfAbsent("kind", inferKind(s));
        req.putIfAbsent("user_id", requestContext.getUserId());
        Map<String, Object> resp = serviceOperations.buildService(s.getServiceRegion(), teamName, serviceAlias, req);
        Object eventId = resp.get("event_id");
        // 更新版本
        s.setUpdateVersion((s.getUpdateVersion() == null ? 1 : s.getUpdateVersion()) + 1);
        s.setUpdateTime(LocalDateTime.now());
        serviceRepo.save(s);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("event_id", eventId);
        bean.put("update_version", s.getUpdateVersion());
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/code/branch", "/code/branch/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult codeBranch(@PathVariable("team_name") String teamName,
                                  @PathVariable("service_alias") String serviceAlias) {
        // 简化版：不调 OAuth git，仅返回当前 code_version 作为唯一已知分支；完整版留给 oauth change
        TenantService s = requireService(serviceAlias);
        return GeneralMessage.okList(List.of(
                Map.of("name", s.getCodeVersion() != null ? s.getCodeVersion() : "main", "current", true)));
    }

    @GetMapping(value = {"/compile_env", "/compile_env/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult getCompileEnv(@PathVariable("team_name") String teamName,
                                     @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(serviceAlias);
        List<TenantServiceEnvVar> rows = envRepo.findByServiceIdAndScope(s.getServiceId(), "build");
        return GeneralMessage.okList(rows.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("attr_name", e.getAttrName());
            m.put("attr_value", e.getAttrValue());
            return m;
        }).toList());
    }

    @PutMapping(value = {"/compile_env", "/compile_env/"})
    @RequirePerm(PermCode.APP_OVERVIEW_CREATE)
    @Transactional
    public ApiResult putCompileEnv(@PathVariable("team_name") String teamName,
                                     @PathVariable("service_alias") String serviceAlias,
                                     @RequestBody Map<String, String> body) {
        TenantService s = requireService(serviceAlias);
        // 全量替换：先删旧 build scope env 再写新
        List<TenantServiceEnvVar> existing = envRepo.findByServiceIdAndScope(s.getServiceId(), "build");
        envRepo.deleteAll(existing);
        for (Map.Entry<String, String> e : body.entrySet()) {
            TenantServiceEnvVar v = new TenantServiceEnvVar();
            v.setTenantId(s.getTenantId());
            v.setServiceId(s.getServiceId());
            v.setName(e.getKey());
            v.setAttrName(e.getKey());
            v.setAttrValue(e.getValue() != null ? e.getValue() : "");
            v.setChange(true);
            v.setScope("build");
            v.setContainerPort(0);
            v.setCreateTime(LocalDateTime.now());
            envRepo.save(v);
        }
        return GeneralMessage.ok();
    }

    private String inferKind(TenantService s) {
        String src = s.getServiceSource() != null ? s.getServiceSource() : "";
        return switch (src) {
            case "source_code" -> "build_from_source_code";
            case "docker_run" -> "build_from_image";
            case "third_party" -> "build_from_third_party";
            default -> "build_from_image";
        };
    }

    private TenantService requireService(String serviceAlias) {
        return serviceRepo.findAll().stream()
                .filter(s -> serviceAlias.equals(s.getServiceAlias()))
                .findFirst()
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }
}
