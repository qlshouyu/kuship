package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentLabelsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组件标签读（对齐 rainbond AppLabelView.get，AppBaseView 登录态）。 */
@RestController
public class ComponentLabelsController {

    private final ComponentLabelsService service;

    public ComponentLabelsController(ComponentLabelsService service) {
        this.service = service;
    }

    /** GET /console/teams/{tenantName}/apps/{serviceAlias}/labels?region_name= */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/labels")
    public ApiResult labels(@PathVariable("tenantName") String tenantName,
                            @PathVariable("serviceAlias") String serviceAlias,
                            @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.bean(200, "success", "查询成功", service.getLabels(serviceAlias, regionName));
    }
}
