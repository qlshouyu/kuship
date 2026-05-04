package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceStatusOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Pod 列表与详情：3 个端点。 */
@RestController
public class AppPodController {

    private final ServiceStatusOperations status;
    private final RuntimeContextLoader loader;
    private final RequestContext requestContext;
    private final ServiceGroupRelationRepository relationRepo;
    private final TenantServiceRepository serviceRepo;

    public AppPodController(ServiceStatusOperations status,
                              RuntimeContextLoader loader,
                              RequestContext requestContext,
                              ServiceGroupRelationRepository relationRepo,
                              TenantServiceRepository serviceRepo) {
        this.status = status;
        this.loader = loader;
        this.requestContext = requestContext;
        this.relationRepo = relationRepo;
        this.serviceRepo = serviceRepo;
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/pods",
                          "/console/teams/{team_name}/apps/{service_alias}/pods/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult list(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias) {
        TenantService s = loader.requireService(teamName, alias);
        String entId = requestContext.getEnterpriseId();
        Map<String, Object> resp = status.getServicePods(s.getServiceRegion(), teamName, alias, entId == null ? "" : entId);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @PostMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/pods/detail",
                            "/console/teams/{team_name}/apps/{service_alias}/pods/detail/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult listByPodNames(@PathVariable("team_name") String teamName,
                                       @PathVariable("service_alias") String alias,
                                       @RequestBody(required = false) Map<String, Object> body) {
        TenantService s = loader.requireService(teamName, alias);
        // body 中 pod_names 透传给 region —— 实现等价于按列表遍历 podDetail
        Map<String, Object> resp = status.getServicePods(s.getServiceRegion(), teamName, alias, "");
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/pods/{pod_name}",
                          "/console/teams/{team_name}/apps/{service_alias}/pods/{pod_name}/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult detailByAlias(@PathVariable("team_name") String teamName,
                                     @PathVariable("service_alias") String alias,
                                     @PathVariable("pod_name") String podName) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> resp = status.podDetail(s.getServiceRegion(), teamName, alias, podName);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/console/teams/{team_name}/groups/{app_id}/pods/{pod_name}",
                          "/console/teams/{team_name}/groups/{app_id}/pods/{pod_name}/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult detailByApp(@PathVariable("team_name") String teamName,
                                   @PathVariable("app_id") Integer appId,
                                   @PathVariable("pod_name") String podName) {
        // 取 app 下任意 service 的 region 作为查询入口
        loader.requireTeam(teamName);
        List<ServiceGroupRelation> rels = relationRepo.findByGroupId(appId);
        if (rels.isEmpty()) {
            throw new ServiceHandleException(404, "app has no services", "应用下没有可定位 region 的组件");
        }
        TenantService anyService = serviceRepo.findByServiceId(rels.get(0).getServiceId())
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
        Map<String, Object> resp = status.podDetail(anyService.getServiceRegion(), teamName,
                anyService.getServiceAlias(), podName);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }
}
