package cn.kuship.console.modules.region.controller.cluster;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import cn.kuship.console.modules.region.service.ClusterNodeService;
import cn.kuship.console.modules.region.service.ClusterNodeService.ClusterNodesResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 集群节点管理端点。
 *
 * <p>对齐 rainbond-console {@code GetNodes / GetNode / NodeAction / NodeLabelsOperate / NodeTaintOperate}，
 * 全部要求 {@link RequireEnterpriseAdmin} 鉴权。
 *
 * <p>URL 规则与 rainbond-console 完全一致：
 * <ul>
 *   <li>GET  /console/enterprise/{enterprise_id}/regions/{region_name}/nodes</li>
 *   <li>GET  /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}</li>
 *   <li>POST /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/action</li>
 *   <li>GET  /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels</li>
 *   <li>PUT  /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels</li>
 *   <li>GET  /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints</li>
 *   <li>PUT  /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints</li>
 * </ul>
 *
 * <p>实现 change：{@code migrate-console-cluster-nodes}
 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}/regions/{region_name}")
public class ClusterNodesController {

    private final ClusterNodeService clusterNodeService;
    private final RegionInfoEntityRepository regionRepo;

    public ClusterNodesController(ClusterNodeService clusterNodeService,
                                   RegionInfoEntityRepository regionRepo) {
        this.clusterNodeService = clusterNodeService;
        this.regionRepo = regionRepo;
    }

    /**
     * 获取集群节点列表及 role 计数。
     *
     * <p>对齐 Python {@code GetNodes.get}：
     * {@code bean=cluster_role_count, list=nodes}
     */
    @GetMapping(value = {"/nodes", "/nodes/"})
    @RequireEnterpriseAdmin
    public ApiResult getNodes(@PathVariable("enterprise_id") String enterpriseId,
                               @PathVariable("region_name") String regionNameOrId) {
        RegionInfo region = requireRegion(enterpriseId, regionNameOrId);
        ClusterNodesResult result = clusterNodeService.getNodesWithRoleCount(
                region.getRegionName(), enterpriseId);
        // 对齐 Python general_message(200, "success", "获取成功", bean=cluster_role_count, list=nodes)
        // bean = cluster_role_count（Map<String,Integer>），list = 节点列表
        Map<String, Object> beanMap = new java.util.LinkedHashMap<>(result.roleCount());
        return GeneralMessage.okWithExtras(beanMap, result.nodes(), null);
    }

    /**
     * 获取单个节点详情。
     *
     * <p>对齐 Python {@code GetNode.get}：{@code bean=res}
     */
    @GetMapping(value = {"/nodes/{node_name}", "/nodes/{node_name}/"})
    @RequireEnterpriseAdmin
    public ApiResult getNode(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("region_name") String regionNameOrId,
                              @PathVariable("node_name") String nodeName) {
        RegionInfo region = requireRegion(enterpriseId, regionNameOrId);
        Map<String, Object> detail = clusterNodeService.getNodeDetail(
                region.getRegionName(), enterpriseId, nodeName);
        return GeneralMessage.ok(detail);
    }

    /**
     * 执行节点动作（cordon / uncordon / drain / evict 等）。
     *
     * <p>对齐 Python {@code NodeAction.post}：白名单校验 action，400 拒绝未知动作。
     */
    @PostMapping(value = {"/nodes/{node_name}/action", "/nodes/{node_name}/action/"})
    @RequireEnterpriseAdmin
    public ApiResult nodeAction(@PathVariable("enterprise_id") String enterpriseId,
                                 @PathVariable("region_name") String regionNameOrId,
                                 @PathVariable("node_name") String nodeName,
                                 @RequestBody(required = false) Map<String, Object> body) {
        String action = body != null ? (String) body.get("action") : null;
        if (action == null || action.isBlank()) {
            throw new ServiceHandleException(400, "action is required", "action 参数不能为空");
        }
        // 白名单校验（ClusterNodeService 内部再次校验并抛 400）
        RegionInfo region = requireRegion(enterpriseId, regionNameOrId);
        Map<String, Object> result = clusterNodeService.operateNode(
                region.getRegionName(), enterpriseId, nodeName, action);
        return GeneralMessage.ok(result);
    }

    /**
     * 获取节点标签。
     *
     * <p>对齐 Python {@code NodeLabelsOperate.get}：{@code bean=body["bean"]}
     */
    @GetMapping(value = {"/nodes/{node_name}/labels", "/nodes/{node_name}/labels/"})
    @RequireEnterpriseAdmin
    public ApiResult getNodeLabels(@PathVariable("enterprise_id") String enterpriseId,
                                    @PathVariable("region_name") String regionNameOrId,
                                    @PathVariable("node_name") String nodeName) {
        RegionInfo region = requireRegion(enterpriseId, regionNameOrId);
        Map<String, Object> labels = clusterNodeService.getNodeLabels(
                region.getRegionName(), enterpriseId, nodeName);
        return GeneralMessage.ok(labels);
    }

    /**
     * 更新节点标签。
     *
     * <p>对齐 Python {@code NodeLabelsOperate.put}：body 取 labels 字段。
     */
    @PutMapping(value = {"/nodes/{node_name}/labels", "/nodes/{node_name}/labels/"})
    @RequireEnterpriseAdmin
    public ApiResult updateNodeLabels(@PathVariable("enterprise_id") String enterpriseId,
                                       @PathVariable("region_name") String regionNameOrId,
                                       @PathVariable("node_name") String nodeName,
                                       @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> labels = body != null
                ? (Map<String, Object>) body.getOrDefault("labels", Map.of())
                : Map.of();
        RegionInfo region = requireRegion(enterpriseId, regionNameOrId);
        Map<String, Object> result = clusterNodeService.updateNodeLabels(
                region.getRegionName(), enterpriseId, nodeName, labels);
        return GeneralMessage.ok(result);
    }

    /**
     * 获取节点污点列表。
     *
     * <p>对齐 Python {@code NodeTaintOperate.get}：{@code list=body["list"]}
     */
    @GetMapping(value = {"/nodes/{node_name}/taints", "/nodes/{node_name}/taints/"})
    @RequireEnterpriseAdmin
    public ApiResult getNodeTaints(@PathVariable("enterprise_id") String enterpriseId,
                                    @PathVariable("region_name") String regionNameOrId,
                                    @PathVariable("node_name") String nodeName) {
        RegionInfo region = requireRegion(enterpriseId, regionNameOrId);
        List<Object> taints = clusterNodeService.getNodeTaints(
                region.getRegionName(), enterpriseId, nodeName);
        return GeneralMessage.okList(taints);
    }

    /**
     * 更新节点污点列表。
     *
     * <p>对齐 Python {@code NodeTaintOperate.put}：body 取 taints 字段（list）。
     */
    @PutMapping(value = {"/nodes/{node_name}/taints", "/nodes/{node_name}/taints/"})
    @RequireEnterpriseAdmin
    public ApiResult updateNodeTaints(@PathVariable("enterprise_id") String enterpriseId,
                                       @PathVariable("region_name") String regionNameOrId,
                                       @PathVariable("node_name") String nodeName,
                                       @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> taints = body != null
                ? (List<Object>) body.getOrDefault("taints", List.of())
                : List.of();
        RegionInfo region = requireRegion(enterpriseId, regionNameOrId);
        List<Object> result = clusterNodeService.updateNodeTaints(
                region.getRegionName(), enterpriseId, nodeName, taints);
        return GeneralMessage.okList(result);
    }

    // ---- 辅助方法 ----

    /**
     * 按 region_name 或 region_id 查找 RegionInfo，校验企业归属。
     */
    private RegionInfo requireRegion(String enterpriseId, String regionNameOrId) {
        // 先尝试按 region_id 查（UUID 格式），再按 region_name 查
        RegionInfo region = regionRepo.findByRegionId(regionNameOrId)
                .or(() -> regionRepo.findByEnterpriseIdAndRegionName(enterpriseId, regionNameOrId))
                .orElseThrow(() -> new ServiceHandleException(404, "region not found", "集群不存在"));
        if (!enterpriseId.equals(region.getEnterpriseId())) {
            throw new ServiceHandleException(403, "region not in enterprise", "集群不属于该企业");
        }
        return region;
    }
}
