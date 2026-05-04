package cn.kuship.console.modules.appcreate.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.appcreate.service.AppDeleteService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code .../apps/{service_alias}/delete}：组件软删除。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class AppDeleteController {

    private final AppDeleteService deleteService;
    private final RequestContext requestContext;

    public AppDeleteController(AppDeleteService deleteService, RequestContext requestContext) {
        this.deleteService = deleteService;
        this.requestContext = requestContext;
    }

    @PostMapping(value = {"/delete", "/delete/"})
    @RequirePerm(PermCode.APP_OVERVIEW_CREATE)
    public ApiResult delete(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias) {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        deleteService.delete(teamName, serviceAlias, userId);
        return GeneralMessage.ok();
    }
}
