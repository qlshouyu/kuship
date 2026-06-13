package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组件状态读（对齐 rainbond AppStatusView，AppBaseView 登录态）。 */
@RestController
public class ComponentStatusController {

    private final ComponentStatusService service;

    public ComponentStatusController(ComponentStatusService service) {
        this.service = service;
    }

    /** GET /console/teams/{tenantName}/apps/{serviceAlias}/status?region_name= */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/status")
    public ApiResult status(@PathVariable("tenantName") String tenantName,
                            @PathVariable("serviceAlias") String serviceAlias,
                            @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.bean(200, "success", "查询成功",
                service.getServiceStatus(tenantName, serviceAlias, regionName));
    }
}
