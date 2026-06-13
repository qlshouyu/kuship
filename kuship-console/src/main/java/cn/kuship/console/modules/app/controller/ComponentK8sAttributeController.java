package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentK8sAttributeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组件 k8s 属性列表读（对齐 rainbond ComponentK8sAttributeListView.get，AppBaseView 登录态）。 */
@RestController
public class ComponentK8sAttributeController {

    private final ComponentK8sAttributeService service;

    public ComponentK8sAttributeController(ComponentK8sAttributeService service) {
        this.service = service;
    }

    /** GET /console/teams/{tenantName}/components/{serviceAlias}/k8s-attributes?region_name= */
    @GetMapping("/console/teams/{tenantName}/components/{serviceAlias}/k8s-attributes")
    public ApiResult list(@PathVariable("tenantName") String tenantName,
                          @PathVariable("serviceAlias") String serviceAlias,
                          @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.list(200, "success", "查询成功", service.listAttributes(serviceAlias));
    }
}
