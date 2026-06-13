package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.region.service.RegionNamespaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 集群命名空间读（对齐 rainbond EnterpriseRegionNamespace，JWTAuthApiView 仅登录）。 */
@RestController
public class RegionNamespaceController {

    private final RegionNamespaceService service;

    public RegionNamespaceController(RegionNamespaceService service) {
        this.service = service;
    }

    /** GET /console/enterprise/{enterprise_id}/regions/{region_id}/namespace?content=all */
    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_id}/namespace")
    public ApiResult namespaces(@PathVariable("enterprise_id") String enterpriseId,
                                @PathVariable("region_id") String regionId,
                                @RequestParam(value = "content", required = false, defaultValue = "all") String content) {
        return GeneralMessage.bean(200, "success", "获取成功", service.listNamespaces(enterpriseId, regionId, content));
    }

    /** GET /console/enterprise/{enterprise_id}/regions/{region_id}/resource?content=all&namespace= */
    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_id}/resource")
    public ApiResult resource(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("region_id") String regionId,
                              @RequestParam(value = "content", required = false, defaultValue = "all") String content,
                              @RequestParam(value = "namespace", required = false, defaultValue = "") String namespace) {
        return GeneralMessage.bean(200, "success", "获取成功",
                service.listNamespaceResources(enterpriseId, regionId, content, namespace));
    }
}
