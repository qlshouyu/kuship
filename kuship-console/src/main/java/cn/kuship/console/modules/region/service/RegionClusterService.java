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
