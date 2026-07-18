package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.region.service.RegionClusterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 集群节点 / Rainbond 组件 / 节点操作（对齐 rainbond GetNodes / RainbondComponents / NodeAction）。
 */
@RestController
public class RegionClusterController {

    private final RegionClusterService service;

    public RegionClusterController(RegionClusterService service) {
        this.service = service;
    }

    /** GET /console/enterprise/{enterprise_id}/regions/{region_name}/nodes */
    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/nodes")
    public ApiResult nodes(@PathVariable("enterprise_id") String enterpriseId,
                           @PathVariable("region_name") String regionName) {
        RegionClusterService.NodesResult r = service.getNodes(regionName);
        return ApiResult.of(200, "success", "获取成功", r.roleCount(), r.nodes());
    }

    /** GET /console/enterprise/{enterprise_id}/regions/{region_name}/rbd-components */
    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/rbd-components")
    public ApiResult rbdComponents(@PathVariable("enterprise_id") String enterpriseId,
                                   @PathVariable("region_name") String regionName) {
        return GeneralMessage.list(200, "success", "获取成功", service.getRbdComponents(regionName));
    }

    /** POST /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/action —— body {action}。 */
    @PostMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/action")
    public ApiResult nodeAction(@PathVariable("enterprise_id") String enterpriseId,
                                @PathVariable("region_name") String regionName,
                                @PathVariable("node_name") String nodeName,
                                @RequestBody(required = false) Map<String, Object> body) {
        String action = body == null ? null : String.valueOf(body.getOrDefault("action", ""));
        Object bean = service.operateNodeAction(regionName, nodeName, action);
        return GeneralMessage.bean(200, "success", "操作成功", bean);
    }
}
