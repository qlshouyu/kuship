package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentDetailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组件详情读（对齐 rainbond AppDetailView.get，AppBaseView 登录态）。 */
@RestController
public class ComponentDetailController {

    private final ComponentDetailService service;

    public ComponentDetailController(ComponentDetailService service) {
        this.service = service;
    }

    /** GET /console/teams/{tenantName}/apps/{serviceAlias}/detail?region_name= */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/detail")
    public ApiResult detail(@PathVariable("tenantName") String tenantName,
                            @PathVariable("serviceAlias") String serviceAlias,
                            @RequestParam(value = "region_name", required = false) String regionName,
                            HttpServletRequest request) {
        return GeneralMessage.bean(200, "success", "查询成功",
                service.getDetail(tenantName, serviceAlias, regionName, request.getServerName()));
    }
}
