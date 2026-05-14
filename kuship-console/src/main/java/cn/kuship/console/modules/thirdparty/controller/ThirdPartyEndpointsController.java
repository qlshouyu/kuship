package cn.kuship.console.modules.thirdparty.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.thirdparty.service.ThirdPartyEndpointService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 第三方组件 endpoint 管理（rainbond {@code urls.py:576} {@code ThirdPartyAppPodsView} 锚点）。
 *
 * <p>路径段 {@code third_party}（下划线）保留 rainbond 历史拼写，与 rainbond URL 一致。
 */
@RestController
@RequestMapping("/console")
public class ThirdPartyEndpointsController {

    private final ThirdPartyEndpointService service;

    public ThirdPartyEndpointsController(ThirdPartyEndpointService service) {
        this.service = service;
    }

    @GetMapping(value = {"/teams/{team_name}/apps/{service_alias}/third_party/pods",
            "/teams/{team_name}/apps/{service_alias}/third_party/pods/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult getEndpoints(@PathVariable("team_name") String teamName,
                                    @PathVariable("service_alias") String serviceAlias) {
        Map<String, Object> bean = service.getEndpoints(teamName, serviceAlias);
        return GeneralMessage.ok(bean);
    }

    @PostMapping(value = {"/teams/{team_name}/apps/{service_alias}/third_party/pods",
            "/teams/{team_name}/apps/{service_alias}/third_party/pods/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult addEndpoints(@PathVariable("team_name") String teamName,
                                    @PathVariable("service_alias") String serviceAlias,
                                    @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> bean = service.postEndpoints(teamName, serviceAlias, body);
        return GeneralMessage.ok(bean);
    }

    @PutMapping(value = {"/teams/{team_name}/apps/{service_alias}/third_party/pods",
            "/teams/{team_name}/apps/{service_alias}/third_party/pods/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult updateEndpoint(@PathVariable("team_name") String teamName,
                                      @PathVariable("service_alias") String serviceAlias,
                                      @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> bean = service.putEndpoints(teamName, serviceAlias, body);
        return GeneralMessage.ok(bean);
    }

    @DeleteMapping(value = {"/teams/{team_name}/apps/{service_alias}/third_party/pods",
            "/teams/{team_name}/apps/{service_alias}/third_party/pods/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult deleteEndpoint(@PathVariable("team_name") String teamName,
                                      @PathVariable("service_alias") String serviceAlias,
                                      @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> bean = service.deleteEndpoints(teamName, serviceAlias, body);
        return GeneralMessage.ok(bean);
    }
}
