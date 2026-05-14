package cn.kuship.console.modules.region.resource.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.response.SkipResponseWrapper;
import cn.kuship.console.infrastructure.region.api.ResourceCenterOperations;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * NS 资源管理端点：类型列表 / 资源列表 / 资源详情 / 创建 / 更新 / 删除。
 *
 * <p>对应 rainbond {@code views/team_resources.py}：
 * {@code NsResourceTypesView} / {@code NsResourcesView} / {@code NsResourceDetailView}。
 */
@RestController
@RequestMapping("/console/teams/{team_name}/regions/{region_name}")
public class NsResourceController {

    private final ResourceCenterOperations ops;

    public NsResourceController(ResourceCenterOperations ops) {
        this.ops = ops;
    }

    /** GET /ns-resource-types — 获取 NS 资源类型列表。 */
    @GetMapping(value = {"/ns-resource-types", "/ns-resource-types/"})
    public ApiResult getNsResourceTypes(@PathVariable("team_name") String teamName,
                                         @PathVariable("region_name") String regionName) {
        Map<String, Object> bean = ops.getNsResourceTypes(regionName, teamName);
        return GeneralMessage.ok(bean);
    }

    /** GET /ns-resources — 获取 NS 资源列表（透传所有 query params）。 */
    @GetMapping(value = {"/ns-resources", "/ns-resources/"})
    public ApiResult getNsResources(@PathVariable("team_name") String teamName,
                                     @PathVariable("region_name") String regionName,
                                     HttpServletRequest request) {
        Map<String, String> params = collectQueryParams(request);
        Map<String, Object> bean = ops.getNsResources(regionName, teamName, params);
        return GeneralMessage.ok(bean);
    }

    /**
     * POST /ns-resources — 创建 NS 资源（raw body + Content-Type 透传）。
     * 透传 region 的 HTTP 状态码（可能非 200）。
     */
    @PostMapping(value = {"/ns-resources", "/ns-resources/"}, consumes = MediaType.ALL_VALUE)
    @SkipResponseWrapper
    public ResponseEntity<byte[]> postNsResource(@PathVariable("team_name") String teamName,
                                                  @PathVariable("region_name") String regionName,
                                                  HttpServletRequest request,
                                                  @RequestBody(required = false) byte[] body) throws IOException {
        Map<String, String> params = collectQueryParams(request);
        String contentType = request.getContentType();
        ResponseEntity<byte[]> resp = ops.postNsResource(regionName, teamName,
                body != null ? body : new byte[0], params, contentType);
        return ResponseEntity.status(resp.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(resp.getBody());
    }

    /** GET /ns-resources/{name} — 获取单个 NS 资源详情。 */
    @GetMapping(value = {"/ns-resources/{name}", "/ns-resources/{name}/"})
    public ApiResult getNsResource(@PathVariable("team_name") String teamName,
                                    @PathVariable("region_name") String regionName,
                                    @PathVariable("name") String name,
                                    HttpServletRequest request) {
        Map<String, String> params = collectQueryParams(request);
        Map<String, Object> bean = ops.getNsResource(regionName, teamName, name, params);
        return GeneralMessage.ok(bean);
    }

    /**
     * PUT /ns-resources/{name} — 更新 NS 资源（raw body + Content-Type 透传）。
     */
    @PutMapping(value = {"/ns-resources/{name}", "/ns-resources/{name}/"}, consumes = MediaType.ALL_VALUE)
    public ApiResult putNsResource(@PathVariable("team_name") String teamName,
                                    @PathVariable("region_name") String regionName,
                                    @PathVariable("name") String name,
                                    HttpServletRequest request,
                                    @RequestBody(required = false) byte[] body) throws IOException {
        Map<String, String> params = collectQueryParams(request);
        String contentType = request.getContentType();
        Map<String, Object> bean = ops.putNsResource(regionName, teamName, name,
                body != null ? body : new byte[0], params, contentType);
        return GeneralMessage.ok(bean);
    }

    /** DELETE /ns-resources/{name} — 删除 NS 资源。 */
    @DeleteMapping(value = {"/ns-resources/{name}", "/ns-resources/{name}/"})
    public ApiResult deleteNsResource(@PathVariable("team_name") String teamName,
                                       @PathVariable("region_name") String regionName,
                                       @PathVariable("name") String name,
                                       HttpServletRequest request) {
        Map<String, String> params = collectQueryParams(request);
        ops.deleteNsResource(regionName, teamName, name, params);
        return GeneralMessage.ok();
    }

    private static Map<String, String> collectQueryParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) params.put(k, v[0]);
        });
        return params;
    }
}
