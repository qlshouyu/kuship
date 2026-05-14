package cn.kuship.console.modules.region.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.ClusterOperations;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 集群节点管理业务层。
 *
 * <p>对齐 rainbond-console {@code enterprise_services.get_nodes} / {@code get_node_detail} 逻辑：
 * 节点状态由 conditions 数组推导，unschedulable=true 追加 {@code ",SchedulingDisabled"}。
 *
 * <p>实现 change：{@code migrate-console-cluster-nodes}
 */
@Service
public class ClusterNodeService {

    /**
     * 支持的节点动作白名单（对齐 Python {@code support_action = ["unschedulable", "reschedulable", "down", "up", "evict"]}）。
     */
    public static final Set<String> SUPPORTED_ACTIONS =
            Set.of("unschedulable", "reschedulable", "down", "up", "evict");

    private final ClusterOperations clusterOperations;
    private final tools.jackson.databind.ObjectMapper json;

    public ClusterNodeService(ClusterOperations clusterOperations) {
        this.clusterOperations = clusterOperations;
        this.json = JsonMapper.builder().build();
    }

    /**
     * 结果 record：节点列表 + role 计数（对应 Python 的 node_list / cluster_role_count）。
     */
    public record ClusterNodesResult(List<Map<String, Object>> nodes,
                                      Map<String, Integer> roleCount) {}

    /**
     * 获取集群节点列表，同时计算各 role 的节点数量（cluster_role_count）。
     *
     * <p>对齐 Python {@code enterprise_services.get_nodes}：
     * <ul>
     *   <li>遍历 conditions，type=Ready + status=True → "Ready"；否则 "NotReady"</li>
     *   <li>unschedulable=true → 追加 ",SchedulingDisabled"</li>
     *   <li>roles 列表累加到 all_node_roles，统计每个 role 的出现次数</li>
     * </ul>
     */
    public ClusterNodesResult getNodesWithRoleCount(String regionName, String enterpriseId) {
        Map<String, Object> raw = clusterOperations.getClusterNodes(regionName, enterpriseId);
        // raw.get("list") 是一个 JsonNode（array）或 List
        Object listObj = raw.get("list");
        List<Map<String, Object>> nodeList = new ArrayList<>();
        List<String> allNodeRoles = new ArrayList<>();

        if (listObj instanceof JsonNode listNode && listNode.isArray()) {
            for (JsonNode node : listNode) {
                nodeList.add(buildNodeSummary(node, allNodeRoles));
            }
        } else if (listObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> nodeMap) {
                    JsonNode nodeNode = json.convertValue(nodeMap, JsonNode.class);
                    nodeList.add(buildNodeSummary(nodeNode, allNodeRoles));
                }
            }
        }

        // 计算 cluster_role_count
        Map<String, Integer> roleCount = new LinkedHashMap<>();
        for (String role : allNodeRoles) {
            roleCount.merge(role, 1, Integer::sum);
        }

        return new ClusterNodesResult(nodeList, roleCount);
    }

    /**
     * 获取单个节点详情，提取 bean 字段并转换为对齐 Python 格式的 Map。
     *
     * <p>对齐 Python {@code enterprise_services.get_node_detail}：
     * <pre>
     * res = {
     *   "name": node["name"],
     *   "ip": node["external_ip"] if node["external_ip"] else node["internal_ip"],
     *   "container_runtime": node["container_run_time"],
     *   "architecture": node["architecture"],
     *   "roles": node["roles"],
     *   "os_version": node["os_version"],
     *   "unschedulable": node["unschedulable"],
     *   "create_time": node["create_time"],
     *   "kernel": node["kernel_version"],
     *   "os_type": node["operating_system"],
     *   "req_cpu": node["resource"]["req_cpu"],
     *   ...
     * }
     * </pre>
     */
    public Map<String, Object> getNodeDetail(String regionName, String enterpriseId, String nodeName) {
        Map<String, Object> beanMap = clusterOperations.getNodeDetail(regionName, enterpriseId, nodeName);
        if (beanMap.isEmpty()) {
            return Map.of();
        }
        JsonNode node = json.convertValue(beanMap, JsonNode.class);
        return buildNodeDetail(node);
    }

    /**
     * 执行节点动作，白名单校验（400 拒绝未知动作）。
     *
     * <p>支持的 action：unschedulable / reschedulable / down / up / evict
     */
    public Map<String, Object> operateNode(String regionName, String enterpriseId,
                                            String nodeName, String action) {
        if (action == null || !SUPPORTED_ACTIONS.contains(action)) {
            throw new ServiceHandleException(400, "unsupported node action: " + action,
                    "暂不支持当前操作，支持: " + SUPPORTED_ACTIONS);
        }
        return clusterOperations.operateNodeAction(regionName, enterpriseId, nodeName, action);
    }

    /** 获取节点标签（透传 region bean）。 */
    public Map<String, Object> getNodeLabels(String regionName, String enterpriseId, String nodeName) {
        return clusterOperations.getNodeLabels(regionName, enterpriseId, nodeName);
    }

    /** 更新节点标签（透传 region）。 */
    public Map<String, Object> updateNodeLabels(String regionName, String enterpriseId,
                                                  String nodeName, Map<String, Object> labels) {
        return clusterOperations.updateNodeLabels(regionName, enterpriseId, nodeName, labels);
    }

    /** 获取节点污点列表（透传 region list）。 */
    public List<Object> getNodeTaints(String regionName, String enterpriseId, String nodeName) {
        return clusterOperations.getNodeTaints(regionName, enterpriseId, nodeName);
    }

    /** 更新节点污点列表（透传 region）。 */
    public List<Object> updateNodeTaints(String regionName, String enterpriseId,
                                           String nodeName, List<Object> taints) {
        return clusterOperations.updateNodeTaints(regionName, enterpriseId, nodeName, taints);
    }

    // ---- 私有辅助 ----

    private Map<String, Object> buildNodeSummary(JsonNode node, List<String> allNodeRoles) {
        String nodeStatus = computeNodeStatus(node);

        // 提取 roles
        JsonNode rolesNode = node.path("roles");
        List<String> roles = new ArrayList<>();
        if (rolesNode.isArray()) {
            for (JsonNode r : rolesNode) {
                roles.add(r.asText());
            }
        }
        allNodeRoles.addAll(roles);

        // 提取 resource
        JsonNode resource = node.path("resource");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", node.path("name").asText(""));
        summary.put("status", nodeStatus);
        summary.put("role", roles);
        summary.put("unschedulable", node.path("unschedulable").asBoolean(false));
        summary.put("arch", node.path("architecture").asText(""));
        summary.put("req_cpu", resource.path("req_cpu").asDouble(0));
        summary.put("cap_cpu", resource.path("cap_cpu").asDouble(0));
        summary.put("req_memory", resource.path("req_memory").asDouble(0) / 1000.0);
        summary.put("cap_memory", resource.path("cap_memory").asDouble(0) / 1000.0);
        return summary;
    }

    private Map<String, Object> buildNodeDetail(JsonNode node) {
        String nodeStatus = computeNodeStatus(node);

        // ip：优先 external_ip，回退 internal_ip（对齐 Python）
        String externalIp = node.path("external_ip").asText("");
        String internalIp = node.path("internal_ip").asText("");
        String ip = (externalIp != null && !externalIp.isBlank()) ? externalIp : internalIp;

        // roles
        JsonNode rolesNode = node.path("roles");
        List<String> roles = new ArrayList<>();
        if (rolesNode.isArray()) {
            for (JsonNode r : rolesNode) {
                roles.add(r.asText());
            }
        }

        JsonNode resource = node.path("resource");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", node.path("name").asText(""));
        detail.put("ip", ip);
        detail.put("container_runtime", node.path("container_run_time").asText(""));
        detail.put("architecture", node.path("architecture").asText(""));
        detail.put("roles", roles);
        detail.put("os_version", node.path("os_version").asText(""));
        detail.put("unschedulable", node.path("unschedulable").asBoolean(false));
        detail.put("create_time", node.path("create_time").asText(""));
        detail.put("kernel", node.path("kernel_version").asText(""));
        detail.put("os_type", node.path("operating_system").asText(""));
        detail.put("status", nodeStatus);
        detail.put("req_cpu", resource.path("req_cpu").asDouble(0));
        detail.put("cap_cpu", resource.path("cap_cpu").asDouble(0));
        detail.put("req_memory", resource.path("req_memory").asDouble(0) / 1000.0);
        detail.put("cap_memory", resource.path("cap_memory").asDouble(0) / 1000.0);
        detail.put("req_root_partition", resource.path("req_disk").asDouble(0) / 1024.0 / 1024.0 / 1024.0);
        detail.put("cap_root_partition", resource.path("cap_disk").asDouble(0) / 1024.0 / 1024.0 / 1024.0);
        detail.put("cap_docker_partition",
                resource.path("cap_container_disk").asDouble(0) / 1024.0 / 1024.0 / 1024.0);
        detail.put("req_docker_partition",
                resource.path("req_container_disk").asDouble(0) / 1024.0 / 1024.0 / 1024.0);
        return detail;
    }

    /**
     * 计算节点状态字符串，对齐 Python 逻辑。
     * conditions 中 type=Ready && status=True → "Ready"；否则 "NotReady"。
     * unschedulable=true → 追加 ",SchedulingDisabled"。
     */
    private String computeNodeStatus(JsonNode node) {
        String nodeStatus = "NotReady";
        JsonNode conditions = node.path("conditions");
        if (conditions.isArray()) {
            for (JsonNode cond : conditions) {
                if ("Ready".equals(cond.path("type").asText(""))
                        && "True".equals(cond.path("status").asText(""))) {
                    nodeStatus = "Ready";
                    break;
                }
            }
        }
        if (node.path("unschedulable").asBoolean(false)) {
            nodeStatus = nodeStatus + ",SchedulingDisabled";
        }
        return nodeStatus;
    }
}
