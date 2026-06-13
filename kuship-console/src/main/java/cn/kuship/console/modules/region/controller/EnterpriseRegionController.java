package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.region.service.EnterpriseRegionReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业集群列表读（对齐 rainbond EnterpriseRegionsLCView.get，safe 级，JWTAuthApiView 仅登录）。
 */
@RestController
public class EnterpriseRegionController {

    private final EnterpriseRegionReadService service;

    public EnterpriseRegionController(EnterpriseRegionReadService service) {
        this.service = service;
    }

    /** GET /console/enterprise/{enterprise_id}/regions?status= */
    @GetMapping("/console/enterprise/{enterprise_id}/regions")
    public ApiResult listRegions(@PathVariable("enterprise_id") String enterpriseId,
                                 @RequestParam(value = "status", required = false) String status) {
        return GeneralMessage.list(200, "success", "获取成功", service.listRegions(enterpriseId, status));
    }
}
