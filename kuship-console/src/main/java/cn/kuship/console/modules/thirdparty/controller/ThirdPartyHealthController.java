package cn.kuship.console.modules.thirdparty.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.thirdparty.service.ThirdPartyEndpointService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 第三方组件健康探针配置（rainbond {@code urls.py:494} {@code ThirdPartyHealthzView} 锚点）。
 *
 * <p>路径段 {@code 3rd-party}（连字符 + 数字简写）保留 rainbond 历史拼写，与 rainbond URL 一致；
 * 与 endpoints 端点 {@code third_party}（下划线）的拼写不一致是 rainbond 历史遗留，本 change
 * 不修复（避免破坏 kuship-ui 的现有调用）。
 */
@RestController
@RequestMapping("/console")
public class ThirdPartyHealthController {

    private final ThirdPartyEndpointService service;

    public ThirdPartyHealthController(ThirdPartyEndpointService service) {
        this.service = service;
    }

    @GetMapping(value = {"/teams/{team_name}/apps/{service_alias}/3rd-party/health",
            "/teams/{team_name}/apps/{service_alias}/3rd-party/health/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult getHealth(@PathVariable("team_name") String teamName,
                                 @PathVariable("service_alias") String serviceAlias) {
        Map<String, Object> bean = service.getHealth(teamName, serviceAlias);
        return GeneralMessage.ok(bean);
    }

    @PutMapping(value = {"/teams/{team_name}/apps/{service_alias}/3rd-party/health",
            "/teams/{team_name}/apps/{service_alias}/3rd-party/health/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    public ApiResult putHealth(@PathVariable("team_name") String teamName,
                                 @PathVariable("service_alias") String serviceAlias,
                                 @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> bean = service.putHealth(teamName, serviceAlias, body);
        return GeneralMessage.ok(bean);
    }
}
