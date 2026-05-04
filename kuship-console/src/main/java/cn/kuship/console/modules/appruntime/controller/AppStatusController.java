package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceStatusOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 组件状态查询：单组件 status。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class AppStatusController {

    private final ServiceStatusOperations status;
    private final RuntimeContextLoader loader;

    public AppStatusController(ServiceStatusOperations status, RuntimeContextLoader loader) {
        this.status = status;
        this.loader = loader;
    }

    @GetMapping(value = {"/status", "/status/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult get(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> resp = status.checkServiceStatus(s.getServiceRegion(), teamName, alias);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }
}
