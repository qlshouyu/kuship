package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.team.entity.TenantRegionInfo;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantRegionInfoRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件实例列表读（对齐 ListAppPodsView.get → region_api.get_service_pods）。
 * region 返回 bean.{new_pods,old_pods}，转换为 {pod_name,pod_status,manage_name,container[]}，
 * 内存 bytes→MB（保留 2 位）、usage_rate=usage*100/limit、跳过 "POD" 容器、主容器置首。
 */
@Service
public class ComponentPodsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TenantServiceInfoRepository serviceRepository;
    private final TenantsRepository tenantsRepository;
    private final TenantRegionInfoRepository tenantRegionInfoRepository;
    private final RegionClient regionClient;

    public ComponentPodsService(TenantServiceInfoRepository serviceRepository,
                                TenantsRepository tenantsRepository,
                                TenantRegionInfoRepository tenantRegionInfoRepository,
                                RegionClient regionClient) {
        this.serviceRepository = serviceRepository;
        this.tenantsRepository = tenantsRepository;
        this.tenantRegionInfoRepository = tenantRegionInfoRepository;
        this.regionClient = regionClient;
    }

    /** 返回 {new_pods:[...], old_pods:[...]}；region bean 为空 → 空 map（对齐 result={}）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listPods(String tenantName, String serviceAlias, String regionName) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        Tenants tenant = tenantsRepository.findByTenantName(tenantName)
                .orElseThrow(() -> ServiceHandleException.notFound("team not found", "团队不存在"));
        String region = (regionName == null || regionName.isBlank()) ? service.getServiceRegion() : regionName;
        String regionTenantName = tenantRegionInfoRepository
                .findByTenantIdAndRegionName(tenant.getTenantId(), region)
                .map(TenantRegionInfo::getRegionTenantName)
                .orElse(tenant.getTenantName());

        String path = "/v2/tenants/" + regionTenantName + "/services/" + serviceAlias
                + "/pods?enterprise_id=" + enc(tenant.getEnterpriseId());
        String body = regionClient.exchange(region, "GET", path, null, TimeoutTier.NORMAL, null);

        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Map<String, Object> bean = (Map<String, Object>) MAPPER.readValue(body, Map.class).get("bean");
            if (bean == null || bean.isEmpty()) {
                return result; // {}
            }
            result.put("new_pods", convertPods((List<Object>) bean.get("new_pods"), service.getK8sComponentName()));
            result.put("old_pods", convertPods((List<Object>) bean.get("old_pods"), service.getK8sComponentName()));
            return result;
        } catch (ServiceHandleException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceHandleException(500, "parse pods failed: " + e.getMessage(), "解析组件实例失败");
        }
    }

    /** 对齐 foobar：data 为 null → null（保留）；否则转换每个 pod。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertPods(List<Object> pods, String k8sComponentName) {
        if (pods == null) {
            return null;
        }
        List<Map<String, Object>> res = new ArrayList<>();
        for (Object o : pods) {
            Map<String, Object> d = (Map<String, Object>) o;
            Map<String, Object> bean = new LinkedHashMap<>();
            bean.put("pod_name", d.get("pod_name"));
            bean.put("pod_status", d.get("pod_status"));
            bean.put("manage_name", "manager");
            Map<String, Object> container = (Map<String, Object>) d.get("container");
            List<Map<String, Object>> containerList = new ArrayList<>();
            if (container != null) {
                for (Map.Entry<String, Object> e : container.entrySet()) {
                    String key = e.getKey();
                    if ("POD".equals(key)) {
                        continue;
                    }
                    Map<String, Object> val = (Map<String, Object>) e.getValue();
                    double memLimit = toMb(val.get("memory_limit"));
                    double memUsage = toMb(val.get("memory_usage"));
                    double usageRate = memLimit != 0 ? memUsage * 100 / memLimit : 0;
                    Map<String, Object> cd = new LinkedHashMap<>();
                    cd.put("container_name", key);
                    cd.put("memory_limit", round2(memLimit));
                    cd.put("memory_usage", round2(memUsage));
                    cd.put("usage_rate", round2(usageRate));
                    containerList.add(cd);
                    // 主容器置首（k8s_component_name 命中且非 default-tcpmesh）
                    if (k8sComponentName != null && key.contains(k8sComponentName) && !key.contains("default-tcpmesh")
                            && containerList.size() > 1) {
                        Map<String, Object> first = containerList.get(0);
                        int last = containerList.size() - 1;
                        containerList.set(0, containerList.get(last));
                        containerList.set(last, first);
                    }
                }
            }
            bean.put("container", containerList);
            res.add(bean);
        }
        return res;
    }

    /** bytes（字符串/数字）→ MB。 */
    private static double toMb(Object bytes) {
        if (bytes == null) {
            return 0;
        }
        double v = bytes instanceof Number n ? n.doubleValue() : Double.parseDouble(bytes.toString());
        return v / 1024 / 1024;
    }

    /** 对齐 Python round(x, 2)（HALF_EVEN）。 */
    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
