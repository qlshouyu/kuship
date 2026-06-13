package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentPortsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组件端口列表读（对齐 rainbond AppPortView.get，AppBaseView 登录态）。 */
@RestController
public class ComponentPortsController {

    private final ComponentPortsService service;

    public ComponentPortsController(ComponentPortsService service) {
        this.service = service;
    }

    /** GET /console/teams/{tenantName}/apps/{serviceAlias}/ports?region_name= */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/ports")
    public ApiResult ports(@PathVariable("tenantName") String tenantName,
                           @PathVariable("serviceAlias") String serviceAlias,
                           @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.list(200, "success", "查询成功", service.listPorts(tenantName, serviceAlias, regionName));
    }
}
