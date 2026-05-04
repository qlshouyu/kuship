package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.context.RequestContext;
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
import java.util.function.BiFunction;

/** 8 个生命周期端点：start / stop / pause / unpause / vm_web / restart / deploy / rollback / upgrade。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class AppLifecycleController {

    private final ServiceLifecycleOperations lifecycle;
    private final TenantServiceRepository serviceRepo;
    private final RuntimeContextLoader loader;
    private final RequestContext requestContext;

    public AppLifecycleController(ServiceLifecycleOperations lifecycle,
                                    TenantServiceRepository serviceRepo,
                                    RuntimeContextLoader loader,
                                    RequestContext requestContext) {
        this.lifecycle = lifecycle;
        this.serviceRepo = serviceRepo;
        this.loader = loader;
        this.requestContext = requestContext;
    }

    @PostMapping(value = {"/start", "/start/"})
    @RequirePerm(PermCode.APP_OVERVIEW_START)
    @Transactional
    public ApiResult start(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias) {
        return invoke(teamName, alias, lifecycle::startService);
    }

    @PostMapping(value = {"/stop", "/stop/"})
    @RequirePerm(PermCode.APP_OVERVIEW_STOP)
    @Transactional
    public ApiResult stop(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias) {
        return invoke(teamName, alias, lifecycle::stopService);
    }

    @PostMapping(value = {"/restart", "/restart/"})
    @RequirePerm(PermCode.APP_OVERVIEW_RESTART)
    @Transactional
    public ApiResult restart(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias) {
        return invoke(teamName, alias, lifecycle::restartService);
    }

    @PostMapping(value = {"/pause", "/pause/"})
    @RequirePerm(PermCode.APP_OVERVIEW_STOP)
    @Transactional
    public ApiResult pause(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias) {
        return invoke(teamName, alias, lifecycle::pauseService);
    }

    @PostMapping(value = {"/unpause", "/unpause/", "/vm_web", "/vm_web/"})
    @RequirePerm(PermCode.APP_OVERVIEW_START)
    @Transactional
    public ApiResult unpause(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias) {
        return invoke(teamName, alias, lifecycle::unpauseService);
    }

    @PostMapping(value = {"/deploy", "/deploy/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DEPLOY)
    @Transactional
    public ApiResult deploy(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias,
                              @RequestBody(required = false) Map<String, Object> body) {
        return invokeWithBody(teamName, alias, body, lifecycle::upgradeService);
    }

    @PostMapping(value = {"/rollback", "/rollback/"})
    @RequirePerm(PermCode.APP_UPGRADE)
    @Transactional
    public ApiResult rollback(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias,
                                @RequestBody(required = false) Map<String, Object> body) {
        return invokeWithBody(teamName, alias, body, lifecycle::rollback);
    }

    @PostMapping(value = {"/upgrade", "/upgrade/"})
    @RequirePerm(PermCode.APP_UPGRADE)
    @Transactional
    public ApiResult upgrade(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias,
                               @RequestBody(required = false) Map<String, Object> body) {
        return invokeWithBody(teamName, alias, body, lifecycle::upgradeService);
    }

    private ApiResult invoke(String teamName, String alias,
                              QuadFn<String, String, String, Map<String, Object>, Map<String, Object>> action) {
        return invokeWithBody(teamName, alias, null, action);
    }

    private ApiResult invokeWithBody(String teamName, String alias, Map<String, Object> body,
                                       QuadFn<String, String, String, Map<String, Object>, Map<String, Object>> action) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> req = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
        req.putIfAbsent("operator", String.valueOf(requestContext.getUserId()));
        Map<String, Object> resp = action.apply(s.getServiceRegion(), teamName, alias, req);
        s.setUpdateVersion((s.getUpdateVersion() == null ? 1 : s.getUpdateVersion()) + 1);
        s.setUpdateTime(LocalDateTime.now());
        serviceRepo.save(s);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("event_id", resp == null ? null : resp.get("event_id"));
        bean.put("update_version", s.getUpdateVersion());
        return GeneralMessage.ok(bean);
    }

    @FunctionalInterface
    private interface QuadFn<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }
}
