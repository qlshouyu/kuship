package cn.kuship.console.infrastructure.region.api;

import cn.kuship.console.infrastructure.region.api.dto.ClusterIdResp;
import cn.kuship.console.infrastructure.region.api.dto.LicenseStatusResp;
import cn.kuship.console.infrastructure.region.api.dto.NamespaceListResp;
import cn.kuship.console.infrastructure.region.api.dto.RegionFeaturesResp;
import cn.kuship.console.infrastructure.region.api.dto.RegionResourceResp;
import cn.kuship.console.infrastructure.region.api.dto.TenantLimitReq;

import java.util.List;
import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Cluster 元信息 / 集群级管理域。<b>实现 change：{@code migrate-console-region-cluster}</b>。
 *
 * <p>本 change 完整实现 8 个 method：getClusterId / activateLicense / getLicenseStatus / getRegionFeatures /
 * getRegionNamespaces / getRegionResources / setTenantLimit / listTenantsInRegion；其余沿用 default 占位。
 * <p>migrate-console-cluster-nodes change 追加 7 个节点管理 method。
 */
public interface ClusterOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-region-cluster";
    String NODES_CHANGE = "migrate-console-cluster-nodes";

    // ---- 8 method 由本 change 落地（ClusterOperationsImpl @Primary 覆盖） ----

    default ClusterIdResp getClusterId(String regionName, String enterpriseId) { return unsupported(IMPLEMENTING_CHANGE); }

    default void activateLicense(String regionName, String enterpriseId, Map<String, Object> body) { unsupported(IMPLEMENTING_CHANGE); }

    default LicenseStatusResp getLicenseStatus(String regionName, String enterpriseId) { return unsupported(IMPLEMENTING_CHANGE); }

    default RegionFeaturesResp getRegionFeatures(String regionName, String tenantName) { return unsupported(IMPLEMENTING_CHANGE); }

    default NamespaceListResp getRegionNamespaces(String regionName, String enterpriseId, String content) { return unsupported(IMPLEMENTING_CHANGE); }

    default RegionResourceResp getRegionResources(String regionName, String enterpriseId) { return unsupported(IMPLEMENTING_CHANGE); }

    default void setTenantLimit(String regionName, String enterpriseId, String tenantName, TenantLimitReq req) { unsupported(IMPLEMENTING_CHANGE); }

    default List<Map<String, Object>> listTenantsInRegion(String regionName, String enterpriseId) { return unsupported(IMPLEMENTING_CHANGE); }

    // ---- 后续 change 占位 ----

    default Map<String, Object> getResources(String regionName, String tenantName, String enterpriseId) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getClusterInfo(String regionName) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getClusterEvents(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    // ---- migrate-console-cluster-nodes：7 个节点管理 method ----

    /**
     * 获取集群节点列表。对应 Python {@code get_cluster_nodes}，region path：{@code GET /v2/cluster/nodes}。
     *
     * @return region 返回的原始节点列表（body.list）数据，包含每个节点的 name / status / roles / architecture / resource 等字段
     */
    default Map<String, Object> getClusterNodes(String regionName, String enterpriseId) { return unsupported(NODES_CHANGE); }

    /**
     * 获取单个节点详情。对应 Python {@code get_node_info}，region path：{@code GET /v2/cluster/nodes/{node_name}/detail}。
     *
     * @return region 返回的原始节点详情（body.bean）
     */
    default Map<String, Object> getNodeDetail(String regionName, String enterpriseId, String nodeName) { return unsupported(NODES_CHANGE); }

    /**
     * 执行节点动作（cordon/uncordon/drain/evict）。对应 Python {@code operate_node_action}，
     * region path：{@code POST /v2/cluster/nodes/{node_name}/action/{action}}。
     * 支持的 action：unschedulable / reschedulable / down / up / evict。
     *
     * @return region 返回的 bean（通常为空 Map 或操作结果）
     */
    default Map<String, Object> operateNodeAction(String regionName, String enterpriseId, String nodeName, String action) { return unsupported(NODES_CHANGE); }

    /**
     * 获取节点标签。对应 Python {@code get_node_labels}，region path：{@code GET /v2/cluster/nodes/{node_name}/labels}。
     *
     * @return region 返回的 bean（含 labels 字段）
     */
    default Map<String, Object> getNodeLabels(String regionName, String enterpriseId, String nodeName) { return unsupported(NODES_CHANGE); }

    /**
     * 更新节点标签。对应 Python {@code update_node_labels}，region path：{@code PUT /v2/cluster/nodes/{node_name}/labels}。
     *
     * @param labels 新的标签 Map（key-value 键值对）
     * @return region 返回的 bean（含更新后的 labels）
     */
    default Map<String, Object> updateNodeLabels(String regionName, String enterpriseId, String nodeName, Map<String, Object> labels) { return unsupported(NODES_CHANGE); }

    /**
     * 获取节点污点列表。对应 Python {@code get_node_taints}，region path：{@code GET /v2/cluster/nodes/{node_name}/taints}。
     *
     * @return region 返回的 list（污点列表）
     */
    default List<Object> getNodeTaints(String regionName, String enterpriseId, String nodeName) { return unsupported(NODES_CHANGE); }

    /**
     * 更新节点污点列表。对应 Python {@code update_node_taints}，region path：{@code PUT /v2/cluster/nodes/{node_name}/taints}。
     *
     * @param taints 污点列表（每个元素含 key/value/effect 字段）
     * @return region 返回的 list（更新后的污点列表）
     */
    default List<Object> updateNodeTaints(String regionName, String enterpriseId, String nodeName, List<Object> taints) { return unsupported(NODES_CHANGE); }
}
