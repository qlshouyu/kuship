package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** trace 端点 GET / POST / DELETE：本阶段简化为 region 透传占位（链路追踪当前未启用）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}/trace")
public class AppTraceController {

    private final RuntimeContextLoader loader;

    public AppTraceController(RuntimeContextLoader loader) {
        this.loader = loader;
    }

    @GetMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult get(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias) {
        loader.requireService(teamName, alias);
        return GeneralMessage.ok(Map.of("enabled", false));
    }

    @PostMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_TELESCOPIC)
    public ApiResult post(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias,
                            @RequestBody(required = false) Map<String, Object> body) {
        loader.requireService(teamName, alias);
        return GeneralMessage.ok(Map.of("enabled", true));
    }

    @DeleteMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_TELESCOPIC)
    public ApiResult delete(@PathVariable("team_name") String teamName, @PathVariable("service_alias") String alias) {
        loader.requireService(teamName, alias);
        return GeneralMessage.ok(Map.of("enabled", false));
    }
}
