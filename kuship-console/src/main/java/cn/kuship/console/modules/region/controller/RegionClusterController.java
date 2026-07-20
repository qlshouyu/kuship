package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.region.service.RegionClusterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    /** GET .../nodes/{node_name} —— 单节点详情（对齐 GetNode，bean=18 字段+status）。 */
    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}")
    public ApiResult nodeDetail(@PathVariable("enterprise_id") String enterpriseId,
                                @PathVariable("region_name") String regionName,
                                @PathVariable("node_name") String nodeName) {
        return GeneralMessage.bean(200, "success", "获取成功", service.getNodeDetail(regionName, nodeName));
    }

    /** GET .../nodes/{node_name}/labels —— 节点标签（对齐 NodeLabelsOperate.get）。 */
    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels")
    public ApiResult nodeLabels(@PathVariable("enterprise_id") String enterpriseId,
                                @PathVariable("region_name") String regionName,
                                @PathVariable("node_name") String nodeName) {
        return GeneralMessage.bean(200, "success", "获取成功", service.getNodeLabels(regionName, nodeName));
    }

    /** PUT .../nodes/{node_name}/labels —— 更新节点标签（对齐 NodeLabelsOperate.put，body.labels 缺省 {}）。 */
    @PutMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels")
    public ApiResult updateNodeLabels(@PathVariable("enterprise_id") String enterpriseId,
                                      @PathVariable("region_name") String regionName,
                                      @PathVariable("node_name") String nodeName,
                                      @RequestBody(required = false) Map<String, Object> body) {
        Object labels = body == null ? Map.of() : body.getOrDefault("labels", Map.of());
        return GeneralMessage.bean(200, "success", "操作成功", service.updateNodeLabels(regionName, nodeName, labels));
    }

    /** GET .../nodes/{node_name}/taints —— 节点污点（对齐 NodeTaintOperate.get：**bean=region 的 list**）。 */
    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints")
    public ApiResult nodeTaints(@PathVariable("enterprise_id") String enterpriseId,
                                @PathVariable("region_name") String regionName,
                                @PathVariable("node_name") String nodeName) {
        return GeneralMessage.bean(200, "success", "获取成功", service.getNodeTaints(regionName, nodeName));
    }

    /** PUT .../nodes/{node_name}/taints —— 更新节点污点（对齐 NodeTaintOperate.put，list=region 的 list）。 */
    @PutMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints")
    public ApiResult updateNodeTaints(@PathVariable("enterprise_id") String enterpriseId,
                                      @PathVariable("region_name") String regionName,
                                      @PathVariable("node_name") String nodeName,
                                      @RequestBody(required = false) Map<String, Object> body) {
        Object taints = body == null ? List.of() : body.getOrDefault("taints", List.of());
        Object result = service.updateNodeTaints(regionName, nodeName, taints);
        return GeneralMessage.list(200, "success", "操作成功",
                result instanceof List<?> l ? l : List.of());
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
