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
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** {@code POST /teams/{team_name}/batch_actions}：批量启动/停止/重启/部署。 */
@RestController
public class AppBatchActionsController {

    private final ServiceLifecycleOperations lifecycle;
    private final TenantServiceRepository serviceRepo;
    private final RuntimeContextLoader loader;

    public AppBatchActionsController(ServiceLifecycleOperations lifecycle,
                                       TenantServiceRepository serviceRepo,
                                       RuntimeContextLoader loader) {
        this.lifecycle = lifecycle;
        this.serviceRepo = serviceRepo;
        this.loader = loader;
    }

    @PostMapping(value = {"/console/teams/{team_name}/batch_actions",
                            "/console/teams/{team_name}/batch_actions/"})
    @RequirePerm(PermCode.APP_OVERVIEW_PERMS)
    public ApiResult batch(@PathVariable("team_name") String teamName,
                             @RequestBody Map<String, Object> body) {
        var team = loader.requireTeam(teamName);
        String action = String.valueOf(body.getOrDefault("action", "")).toLowerCase(Locale.ROOT);
        if (action.isEmpty()) {
            throw new ServiceHandleException(400, "missing action", "缺少 action 参数");
        }
        Set<String> serviceIds = collectServiceIds(body);
        if (serviceIds.isEmpty()) {
            throw new ServiceHandleException(400, "service_ids required", "service_ids 或 service_alias_list 不能为空");
        }
        List<TenantService> services = serviceRepo.findByServiceIdIn(new ArrayList<>(serviceIds));
        // 校验所有 service 都属于该 team
        services.forEach(s -> {
            if (!Objects.equals(s.getTenantId(), team.getTenantId())) {
                throw new ServiceHandleException(403, "service not in team", "部分组件不属于当前团队");
            }
        });

        List<String> success = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (TenantService s : services) {
            try {
                Map<String, Object> req = new LinkedHashMap<>();
                req.put("operator", String.valueOf(body.getOrDefault("operator", "")));
                switch (action) {
                    case "start" -> lifecycle.startService(s.getServiceRegion(), teamName, s.getServiceAlias(), req);
                    case "stop" -> lifecycle.stopService(s.getServiceRegion(), teamName, s.getServiceAlias(), req);
                    case "restart" -> lifecycle.restartService(s.getServiceRegion(), teamName, s.getServiceAlias(), req);
                    case "deploy", "upgrade" -> lifecycle.upgradeService(s.getServiceRegion(), teamName, s.getServiceAlias(), req);
                    default -> throw new ServiceHandleException(400, "unsupported action", "不支持的批量动作：" + action);
                }
                success.add(s.getServiceId());
            } catch (Exception e) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("service_id", s.getServiceId());
                f.put("msg", e.getMessage());
                failed.add(f);
            }
        }
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("success", success);
        bean.put("failed", failed);
        return GeneralMessage.ok(bean);
    }

    @SuppressWarnings("unchecked")
    private Set<String> collectServiceIds(Map<String, Object> body) {
        Set<String> ids = new LinkedHashSet<>();
        Object sids = body.get("service_ids");
        if (sids instanceof List<?> l) l.forEach(o -> { if (o != null) ids.add(o.toString()); });
        Object aliases = body.get("service_alias_list");
        if (aliases instanceof List<?> al) {
            for (Object o : al) {
                if (o == null) continue;
                serviceRepo.findAll().stream()
                        .filter(s -> o.toString().equals(s.getServiceAlias()))
                        .findFirst()
                        .ifPresent(s -> ids.add(s.getServiceId()));
            }
        }
        return ids;
    }
}
