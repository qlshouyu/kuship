package cn.kuship.console.modules.enterprise.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.enterprise.service.EnterpriseTeamOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 企业团队概览（对齐 rainbond EnterpriseTeamOverView，JWTAuthApiView 仅登录）。
 */
@RestController
public class EnterpriseTeamOverviewController {

    private final EnterpriseTeamOverviewService service;
    private final RequestContext requestContext;

    public EnterpriseTeamOverviewController(EnterpriseTeamOverviewService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    /** GET /console/enterprise/{enterprise_id}/overview/team */
    @GetMapping("/console/enterprise/{enterprise_id}/overview/team")
    public ApiResult overviewTeam(@PathVariable("enterprise_id") String enterpriseId) {
        Map<String, Object> bean = service.overviewTeam(enterpriseId, requestContext.getCurrentUser().getUserId());
        return GeneralMessage.bean(200, "success", null, bean);
    }
}
