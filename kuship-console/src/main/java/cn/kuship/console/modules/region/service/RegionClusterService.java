package cn.kuship.console.modules.region.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集群节点 / Rainbond 组件读 + 节点操作（对齐 rainbond GetNodes / RainbondComponents / NodeAction）。
 * 全部经 region-api：/v2/cluster/nodes、/v2/cluster/rbd-components、/v2/cluster/nodes/{node}/action/{action}。
 */
@Service
public class RegionClusterService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> SUPPORT_ACTIONS =
            List.of("unschedulable", "reschedulable", "down", "up", "evict");

    private final RegionClient regionClient;

    public RegionClusterService(RegionClient regionClient) {
        this.regionClient = regionClient;
    }

    /** 节点列表 + 各角色计数（对齐 enterprise_services.get_nodes）。 */
    @SuppressWarnings("unchecked")
    public NodesResult getNodes(String regionName) {
        String body = regionClient.exchange(regionName, "GET", "/v2/cluster/nodes",
                null, TimeoutTier.NORMAL, null);
        List<Object> nodes = readList(body);
        List<Map<String, Object>> nodeList = new ArrayList<>();
        List<String> allRoles = new ArrayList<>();
        for (Object o : nodes) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> node = (Map<String, Object>) o;
            String status = "NotReady";
            Object conds = node.get("conditions");
            if (conds instanceof List<?> cl) {
                for (Object c : cl) {
                    if (c instanceof Map<?, ?> cm
                            && "Ready".equals(cm.get("type")) && "True".equals(cm.get("status"))) {
                        status = "Ready";
                    }
                }
            }
            boolean unschedulable = Boolean.TRUE.equals(node.get("unschedulable"));
            if (unschedulable) {
                status = status + ",SchedulingDisabled";
            }
            List<Object> roles = node.get("roles") instanceof List<?> rl ? new ArrayList<>(rl) : List.of();
            Map<String, Object> resource = node.get("resource") instanceof Map<?, ?> rm
                    ? (Map<String, Object>) rm : Map.of();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", node.get("name"));
            m.put("status", status);
            m.put("role", roles);
            m.put("unschedulable", unschedulable);
            m.put("arch", node.get("architecture"));
            m.put("req_cpu", resource.get("req_cpu"));
            m.put("cap_cpu", resource.get("cap_cpu"));
            m.put("req_memory", toDouble(resource.get("req_memory")) / 1000);
            m.put("cap_memory", toDouble(resource.get("cap_memory")) / 1000);
            nodeList.add(m);
            for (Object r : roles) {
                allRoles.add(String.valueOf(r));
            }
        }
        Map<String, Object> roleCount = new LinkedHashMap<>();
        for (String role : allRoles) {
            roleCount.merge(role, 1, (a, b) -> ((Number) a).intValue() + 1);
        }
        return new NodesResult(roleCount, nodeList);
    }

    /** Rainbond 平台组件 + pod 健康（对齐 enterprise_services.get_rbdcomponents）。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRbdComponents(String regionName) {
        String body = regionClient.exchange(regionName, "GET", "/v2/cluster/rbd-components",
                null, TimeoutTier.NORMAL, null);
        List<Object> components = readList(body);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : components) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> component = (Map<String, Object>) o;
            int runPods = toInt(component.get("run_pods"));
            int allPods = toInt(component.get("all_pods"));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", component.get("name"));
            info.put("run_pods", component.get("run_pods"));
            info.put("all_pods", component.get("all_pods"));
            String status = runPods == allPods ? "Running" : "Abnormal";

            List<Map<String, Object>> podList = new ArrayList<>();
            int succeed = 0;
            Object podsObj = component.get("pods");
            if (podsObj instanceof List<?> pods) {
                for (Object p : pods) {
                    if (!(p instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> pod = (Map<String, Object>) p;
                    Map<String, Object> meta = asMap(pod.get("metadata"));
                    Map<String, Object> st = asMap(pod.get("status"));
                    String phase = String.valueOf(st.get("phase"));
                    List<Object> ctrStatuses = st.get("containerStatuses") instanceof List<?> cs
                            ? new ArrayList<>(cs) : List.of();
                    int runContainer = 0;
                    int restartCount = 0;
                    for (Object cObj : ctrStatuses) {
                        Map<String, Object> ctr = asMap(cObj);
                        restartCount += toInt(ctr.get("restartCount"));
                        if (asMap(ctr.get("state")).containsKey("running")) {
                            runContainer++;
                        }
                    }
                    Map<String, Object> podInfo = new LinkedHashMap<>();
                    podInfo.put("pod_name", meta.get("name"));
                    podInfo.put("create_time", meta.get("creationTimestamp"));
                    podInfo.put("status", phase);
                    podInfo.put("pod_ip", st.get("podIP"));
                    podInfo.put("all_container", ctrStatuses.size());
                    podInfo.put("run_container", runContainer);
                    podInfo.put("restart_count", restartCount);
                    if ("Succeeded".equals(phase)) {
                        succeed++;
                    }
                    podList.add(podInfo);
                }
            }
            if (succeed == allPods) {
                status = "Completed";
            }
            info.put("status", status);
            info.put("pods", podList);
            out.add(info);
        }
        return out;
    }

    /** 单节点详情（对齐 enterprise_services.get_node_detail：region /v2/cluster/nodes/{n}/detail 的 bean 重排+换算）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getNodeDetail(String regionName, String nodeName) {
        String body = regionClient.exchange(regionName, "GET",
                "/v2/cluster/nodes/" + nodeName + "/detail", null, TimeoutTier.NORMAL, null);
        Map<String, Object> node;
        try {
            Object bean = MAPPER.readValue(body, Map.class).get("bean");
            node = bean instanceof Map ? (Map<String, Object>) bean : Map.of();
        } catch (Exception e) {
            node = Map.of();
        }
        Map<String, Object> resource = asMap(node.get("resource"));
        Object externalIp = node.get("external_ip");
        boolean hasExternal = externalIp != null && !"".equals(externalIp);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("name", node.get("name"));
        res.put("ip", hasExternal ? externalIp : node.get("internal_ip"));
        res.put("container_runtime", node.get("container_run_time"));
        res.put("architecture", node.get("architecture"));
        res.put("roles", node.get("roles"));
        res.put("os_version", node.get("os_version"));
        boolean unschedulable = Boolean.TRUE.equals(node.get("unschedulable"));
        res.put("unschedulable", unschedulable);
        res.put("create_time", node.get("create_time"));
        res.put("kernel", node.get("kernel_version"));
        res.put("os_type", node.get("operating_system"));
        res.put("req_cpu", resource.get("req_cpu"));
        res.put("cap_cpu", resource.get("cap_cpu"));
        res.put("req_memory", toDouble(resource.get("req_memory")) / 1000);
        res.put("cap_memory", toDouble(resource.get("cap_memory")) / 1000);
        res.put("req_root_partition", toDouble(resource.get("req_disk")) / 1024 / 1024 / 1024);
        res.put("cap_root_partition", toDouble(resource.get("cap_disk")) / 1024 / 1024 / 1024);
        res.put("cap_docker_partition", toDouble(resource.get("cap_container_disk")) / 1024 / 1024 / 1024);
        res.put("req_docker_partition", toDouble(resource.get("req_container_disk")) / 1024 / 1024 / 1024);
        String status = "NotReady";
        if (node.get("conditions") instanceof List<?> cl) {
            for (Object c : cl) {
                if (c instanceof Map<?, ?> cm && "Ready".equals(cm.get("type")) && "True".equals(cm.get("status"))) {
                    status = "Ready";
                }
            }
        }
        if (unschedulable) {
            status = status + ",SchedulingDisabled";
        }
        res.put("status", status);
        return res;
    }

    /** 节点标签读（对齐 NodeLabelsOperate.get：bean=region body.bean）。 */
    public Object getNodeLabels(String regionName, String nodeName) {
        return regionField("GET", regionName, "/v2/cluster/nodes/" + nodeName + "/labels", null, "bean");
    }

    /** 节点标签写（对齐 NodeLabelsOperate.put：region body=labels 本体，bean=body.bean）。 */
    public Object updateNodeLabels(String regionName, String nodeName, Object labels) {
        return regionField("PUT", regionName, "/v2/cluster/nodes/" + nodeName + "/labels", labels, "bean");
    }

    /** 节点污点读（对齐 NodeTaintOperate.get：**bean**=region body.list，rainbond 原样怪癖）。 */
    public Object getNodeTaints(String regionName, String nodeName) {
        return regionField("GET", regionName, "/v2/cluster/nodes/" + nodeName + "/taints", null, "list");
    }

    /** 节点污点写（对齐 NodeTaintOperate.put：region body=taints 本体，list=body.list）。 */
    public Object updateNodeTaints(String regionName, String nodeName, Object taints) {
        return regionField("PUT", regionName, "/v2/cluster/nodes/" + nodeName + "/taints", taints, "list");
    }

    private Object regionField(String method, String regionName, String path, Object body, String field) {
        String resp = regionClient.exchange(regionName, method, path,
                body == null ? null : toJson(body), TimeoutTier.NORMAL, null);
        try {
            return MAPPER.readValue(resp, Map.class).get(field);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "null";
        }
    }

    /** 节点操作（对齐 NodeAction）。action 非法 → 抛 400「暂不支持当前操作」；否则 POST region 并回传 bean。 */
    public Object operateNodeAction(String regionName, String nodeName, String action) {
        if (action == null || !SUPPORT_ACTIONS.contains(action)) {
            throw new ServiceHandleException(400, "failed", "暂不支持当前操作");
        }
        String body = regionClient.exchange(regionName, "POST",
                "/v2/cluster/nodes/" + nodeName + "/action/" + action, null, TimeoutTier.NORMAL, null);
        try {
            return MAPPER.readValue(body, Object.class);
        } catch (Exception e) {
            return body;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> readList(String body) {
        try {
            Object listObj = MAPPER.readValue(body, Map.class).get("list");
            if (listObj instanceof List<?> l) {
                return new ArrayList<>((List<Object>) l);
            }
        } catch (Exception ignored) {
            // region 返回非预期结构 → 空
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    private static double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0d;
    }

    private static int toInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    /** 节点列表结果：bean=各角色计数，list=节点列表。 */
    public record NodesResult(Map<String, Object> roleCount, List<Map<String, Object>> nodes) {
    }
}
