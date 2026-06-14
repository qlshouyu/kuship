package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentAutoscalerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组件自动伸缩规则列表读（对齐 rainbond ListAppAutoscalerView.get，AppBaseView 登录态）。 */
@RestController
public class ComponentAutoscalerController {

    private final ComponentAutoscalerService service;

    public ComponentAutoscalerController(ComponentAutoscalerService service) {
        this.service = service;
    }

    /** GET /console/teams/{tenantName}/apps/{serviceAlias}/xparules?region_name= */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/xparules")
    public ApiResult rules(@PathVariable("tenantName") String tenantName,
                           @PathVariable("serviceAlias") String serviceAlias,
                           @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.list(200, "success", "查询成功", service.listRules(serviceAlias));
    }
}
