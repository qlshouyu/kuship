package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentDependencyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组件依赖读（对齐 rainbond AppDependencyView/AppDependencyViewList，AppBaseView 登录态）。 */
@RestController
public class ComponentDependencyController {

    private final ComponentDependencyService service;

    public ComponentDependencyController(ComponentDependencyService service) {
        this.service = service;
    }

    /** GET .../dependency（正向：本组件依赖的组件）。bean={port_list,total}。 */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/dependency")
    public ApiResult dependency(@PathVariable("tenantName") String tenantName,
                                @PathVariable("serviceAlias") String serviceAlias,
                                @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                @RequestParam(value = "page_size", required = false, defaultValue = "25") int pageSize,
                                @RequestParam(value = "region_name", required = false) String regionName) {
        ComponentDependencyService.Result r = service.forward(serviceAlias, page, pageSize);
        return GeneralMessage.bean(200, "success", "查询成功", r.bean())
                .putExtra("list", r.list()).putExtra("total", r.total());
    }

    /** GET .../dependency-list（反向：依赖本组件的组件）。bean={service_id,port_list,total}。 */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/dependency-list")
    public ApiResult dependencyList(@PathVariable("tenantName") String tenantName,
                                    @PathVariable("serviceAlias") String serviceAlias,
                                    @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                    @RequestParam(value = "page_size", required = false, defaultValue = "25") int pageSize,
                                    @RequestParam(value = "region_name", required = false) String regionName) {
        ComponentDependencyService.Result r = service.reverse(serviceAlias, page, pageSize);
        return GeneralMessage.bean(200, "success", "查询成功", r.bean())
                .putExtra("list", r.list()).putExtra("total", r.total());
    }
}
