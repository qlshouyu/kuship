package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 访问入口查询：单组件访问入口 / 应用整体可访问 service 列表。 */
@RestController
public class AppVisitController {

    private final RuntimeContextLoader loader;

    public AppVisitController(RuntimeContextLoader loader) {
        this.loader = loader;
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/visit",
                          "/console/teams/{team_name}/apps/{service_alias}/visit/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult visit(@PathVariable("team_name") String teamName,
                             @PathVariable("service_alias") String alias) {
        loader.requireService(teamName, alias);
        // ports 子资源由 application-core 维护；此处仅返回空入口，由前端 + ports 接口拼接 URL
        return GeneralMessage.okList(List.of());
    }

    @GetMapping(value = {"/console/teams/{team_name}/groups/{group_id}/visit",
                          "/console/teams/{team_name}/groups/{group_id}/visit/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult groupVisit(@PathVariable("team_name") String teamName,
                                  @PathVariable("group_id") Integer groupId) {
        loader.requireTeam(teamName);
        return GeneralMessage.okList(List.of());
    }
}
