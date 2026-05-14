package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.SkipResponseWrapper;
import cn.kuship.console.modules.gateway.service.GatewayProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * API Gateway 透传代理（路径 tail 传给 region）。
 * rainbond 锚点: {@code urls.py:191 AppApiGatewayView}
 * 路径: {@code /api-gateway/v1/{tenant_name}/**}（无 /console 前缀）
 *
 * <p>SecurityConfig 把 {@code /api-gateway/v1/**} 设为 {@code authenticated}（需要 JWT）。
 */
@RestController
@RequestMapping({
        "/api-gateway/v1/{tenant_name}/**"
})
public class AppApiGatewayController {

    private final GatewayProxyService proxyService;
    private final RequestContext ctx;

    public AppApiGatewayController(GatewayProxyService proxyService,
                                    RequestContext ctx) {
        this.proxyService = proxyService;
        this.ctx = ctx;
    }

    @GetMapping
    @SkipResponseWrapper
    public Map<String, Object> get(@PathVariable("tenant_name") String tenantName,
                                    @RequestParam(required = false) String region_name,
                                    HttpServletRequest request) {
        String path = extractPathTail(request);
        String regionName = region_name != null ? region_name : "";
        return proxyService.proxyGet(regionName, ctx.getEnterpriseId(), tenantName, path);
    }

    @PostMapping
    @SkipResponseWrapper
    public Map<String, Object> post(@PathVariable("tenant_name") String tenantName,
                                     @RequestParam(required = false) String region_name,
                                     @RequestBody(required = false) Map<String, Object> body,
                                     HttpServletRequest request) {
        String path = extractPathTail(request);
        String regionName = region_name != null ? region_name : "";
        return proxyService.proxyPost(regionName, ctx.getEnterpriseId(), tenantName, path, body);
    }

    @PutMapping
    @SkipResponseWrapper
    public Map<String, Object> put(@PathVariable("tenant_name") String tenantName,
                                    @RequestParam(required = false) String region_name,
                                    @RequestBody(required = false) Map<String, Object> body,
                                    HttpServletRequest request) {
        String path = extractPathTail(request);
        String regionName = region_name != null ? region_name : "";
        return proxyService.proxyPut(regionName, ctx.getEnterpriseId(), tenantName, path, body);
    }

    @DeleteMapping
    @SkipResponseWrapper
    public Map<String, Object> delete(@PathVariable("tenant_name") String tenantName,
                                       @RequestParam(required = false) String region_name,
                                       @RequestBody(required = false) Map<String, Object> body,
                                       HttpServletRequest request) {
        String path = extractPathTail(request);
        String regionName = region_name != null ? region_name : "";
        return proxyService.proxyDelete(regionName, ctx.getEnterpriseId(), tenantName, path, body);
    }

    /**
     * 从 Spring HandlerMapping 属性取出 {@code **} 匹配的剩余路径，
     * 拼回完整 path 传给 region。
     */
    private static String extractPathTail(HttpServletRequest request) {
        Object attr = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (attr == null) return request.getRequestURI();
        return attr.toString();
    }
}
