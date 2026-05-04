package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceLogOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 日志查询：log（实时尾部）/ log_instance（拿 ws token）/ history_log / logs。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class AppLogController {

    private final ServiceLogOperations log;
    private final RuntimeContextLoader loader;

    public AppLogController(ServiceLogOperations log, RuntimeContextLoader loader) {
        this.log = log;
        this.loader = loader;
    }

    @GetMapping(value = {"/log", "/log/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult log(@PathVariable("team_name") String teamName,
                            @PathVariable("service_alias") String alias,
                            @RequestParam(value = "lines", required = false) Integer lines,
                            @RequestParam(value = "pod_name", required = false) String podName) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> body = new LinkedHashMap<>();
        if (lines != null) body.put("lines", lines);
        if (podName != null) body.put("pod_name", podName);
        Map<String, Object> resp = log.getServiceLogs(s.getServiceRegion(), teamName, alias, body);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/log_instance", "/log_instance/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult logInstance(@PathVariable("team_name") String teamName,
                                    @PathVariable("service_alias") String alias) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> resp = log.getDockerLogInstance(s.getServiceRegion(), teamName, alias);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/history_log", "/history_log/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult historyLog(@PathVariable("team_name") String teamName,
                                  @PathVariable("service_alias") String alias) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> resp = log.getServiceLogFiles(s.getServiceRegion(), teamName, alias);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }

    @GetMapping(value = {"/logs", "/logs/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult logs(@PathVariable("team_name") String teamName,
                            @PathVariable("service_alias") String alias,
                            @RequestParam Map<String, String> queryParams) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> body = new LinkedHashMap<>(queryParams);
        Map<String, Object> resp = log.getServiceLogs(s.getServiceRegion(), teamName, alias, body);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }
}
