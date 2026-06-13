package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentBriefService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组件概览读（对齐 rainbond AppBriefView.get，AppBaseView 登录态）。 */
@RestController
public class ComponentBriefController {

    private final ComponentBriefService service;

    public ComponentBriefController(ComponentBriefService service) {
        this.service = service;
    }

    /** GET /console/teams/{tenantName}/apps/{serviceAlias}/brief?region_name= */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/brief")
    public ApiResult brief(@PathVariable("tenantName") String tenantName,
                           @PathVariable("serviceAlias") String serviceAlias,
                           @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.bean(200, "success", "查询成功", service.getBrief(serviceAlias));
    }
}
