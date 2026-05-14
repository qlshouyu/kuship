package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.gateway.service.GatewayProxyService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 旧域名 → ApisixRoute 格式转换。
 * rainbond 锚点: {@code urls.py:192 AppApiGatewayConvertView}
 * 路径: {@code /api-gateway/convert}（无 /console 前缀）
 */
@RestController
@RequestMapping({
        "/api-gateway/convert",
        "/api-gateway/convert/"
})
public class AppApiGatewayConvertController {

    private final GatewayProxyService proxyService;
    private final RequestContext ctx;

    public AppApiGatewayConvertController(GatewayProxyService proxyService,
                                           RequestContext ctx) {
        this.proxyService = proxyService;
        this.ctx = ctx;
    }

    @PostMapping
    public Map<String, Object> convert(@RequestParam(required = false) String region_name,
                                        @RequestBody Map<String, Object> body) {
        String regionName = region_name != null ? region_name : "";
        return proxyService.convert(regionName, ctx.getEnterpriseId(), body);
    }
}
