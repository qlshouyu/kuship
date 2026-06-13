package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentProbeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 组件探针读（对齐 rainbond AppProbeView.get，AppBaseView 登录态）。 */
@RestController
public class ComponentProbeController {

    private final ComponentProbeService service;

    public ComponentProbeController(ComponentProbeService service) {
        this.service = service;
    }

    /** GET .../apps/{serviceAlias}/probe?mode= 。无探针→body code=404(HTTP 200)。 */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/probe")
    public ApiResult probe(@PathVariable("tenantName") String tenantName,
                           @PathVariable("serviceAlias") String serviceAlias,
                           @RequestParam(value = "mode", required = false) String mode,
                           @RequestParam(value = "region_name", required = false) String regionName) {
        Map<String, Object> probe = service.getProbe(serviceAlias, mode);
        if (probe == null) {
            return GeneralMessage.message(404, "get probe error", "探针不存在，您可能并未设置检测探针");
        }
        return GeneralMessage.bean(200, "success", "查询成功", probe);
    }
}
