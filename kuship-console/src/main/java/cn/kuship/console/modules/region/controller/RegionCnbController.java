package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.region.service.RegionCnbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 集群 CNB 框架读（对齐 rainbond EnterpriseRegionCNBFrameworks，JWTAuthApiView 仅登录）。 */
@RestController
public class RegionCnbController {

    private static final Logger log = LoggerFactory.getLogger(RegionCnbController.class);

    private final RegionCnbService service;

    public RegionCnbController(RegionCnbService service) {
        this.service = service;
    }

    /** GET /console/enterprise/{enterprise_id}/regions/{region_id}/cnb/frameworks?lang=nodejs */
    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_id}/cnb/frameworks")
    public ApiResult frameworks(@PathVariable("enterprise_id") String enterpriseId,
                                @PathVariable("region_id") String regionId,
                                @RequestParam(value = "lang", required = false, defaultValue = "nodejs") String lang) {
        try {
            return GeneralMessage.list(200, "success", "获取成功", service.showFrameworks(enterpriseId, regionId, lang));
        } catch (Exception e) {
            // 对齐 rainbond view：异常统一回退 400 failed
            log.warn("get cnb frameworks failed", e);
            return GeneralMessage.message(400, "failed", "获取CNB框架列表失败");
        }
    }
}
