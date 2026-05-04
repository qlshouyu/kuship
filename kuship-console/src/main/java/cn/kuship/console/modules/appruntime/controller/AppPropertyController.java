package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceLifecycleOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 3 个属性变更端点：deploytype / change/service_name / set/is_upgrade。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class AppPropertyController {

    private final ServiceLifecycleOperations lifecycle;
    private final TenantServiceRepository serviceRepo;
    private final RuntimeContextLoader loader;

    public AppPropertyController(ServiceLifecycleOperations lifecycle,
                                   TenantServiceRepository serviceRepo,
                                   RuntimeContextLoader loader) {
        this.lifecycle = lifecycle;
        this.serviceRepo = serviceRepo;
        this.loader = loader;
    }

    @PutMapping(value = {"/deploytype", "/deploytype/"})
    @RequirePerm(PermCode.APP_OVERVIEW_TELESCOPIC)
    @Transactional
    public ApiResult deploytype(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias,
                                  @RequestBody Map<String, Object> body) {
        TenantService s = loader.requireService(teamName, alias);
        Object t = body.get("deploy_type");
        if (!(t instanceof String type)) {
            throw new ServiceHandleException(400, "missing deploy_type", "缺少 deploy_type 参数");
        }
        s.setServiceType(type);
        s.setUpdateTime(LocalDateTime.now());
        serviceRepo.save(s);
        Map<String, Object> resp = lifecycle.changeMemory(s.getServiceRegion(), teamName, alias, body);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("deploy_type", type);
        bean.put("region", resp);
        return GeneralMessage.ok(bean);
    }

    @PutMapping(value = {"/change/service_name", "/change/service_name/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    @Transactional
    public ApiResult changeServiceName(@PathVariable("team_name") String teamName,
                                         @PathVariable("service_alias") String alias,
                                         @RequestBody Map<String, Object> body) {
        TenantService s = loader.requireService(teamName, alias);
        if (body.get("service_name") instanceof String n) s.setServiceName(n);
        if (body.get("k8s_component_name") instanceof String k) s.setK8sComponentName(k);
        s.setUpdateTime(LocalDateTime.now());
        serviceRepo.save(s);
        return GeneralMessage.ok(Map.of(
                "service_name", s.getServiceName() != null ? s.getServiceName() : "",
                "k8s_component_name", s.getK8sComponentName() != null ? s.getK8sComponentName() : ""));
    }

    @PutMapping(value = {"/set/is_upgrade", "/set/is_upgrade/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    @Transactional
    public ApiResult setIsUpgrade(@PathVariable("team_name") String teamName,
                                    @PathVariable("service_alias") String alias,
                                    @RequestBody Map<String, Object> body) {
        TenantService s = loader.requireService(teamName, alias);
        Object v = body.get("is_upgrade");
        boolean upgrade = v instanceof Boolean ? (Boolean) v
                : v != null && Boolean.parseBoolean(v.toString());
        s.setBuildUpgrade(upgrade);
        s.setUpdateTime(LocalDateTime.now());
        serviceRepo.save(s);
        return GeneralMessage.ok(Map.of("is_upgrade", upgrade));
    }
}
