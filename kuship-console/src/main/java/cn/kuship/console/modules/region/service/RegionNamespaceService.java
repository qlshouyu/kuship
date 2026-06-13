package cn.kuship.console.modules.region.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 集群命名空间读（对齐 EnterpriseRegionNamespace + region_api.list_namespaces）。 */
@Service
public class RegionNamespaceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RegionConfigRepository regionConfigRepository;
    private final RegionClient regionClient;

    public RegionNamespaceService(RegionConfigRepository regionConfigRepository, RegionClient regionClient) {
        this.regionConfigRepository = regionConfigRepository;
        this.regionClient = regionClient;
    }

    /** region /v2/cluster/namespace?eid&content → body.list（放入 bean）。 */
    @SuppressWarnings("unchecked")
    public Object listNamespaces(String enterpriseId, String regionId, String content) {
        RegionConfig region = regionConfigRepository.findByRegionId(regionId)
                .orElseThrow(() -> ServiceHandleException.notFound("region not found", "数据中心不存在"));
        String path = "/v2/cluster/namespace?eid=" + enc(enterpriseId) + "&content=" + enc(content == null ? "all" : content);
        String body = regionClient.exchange(region.getRegionName(), "GET", path, null, TimeoutTier.NORMAL, null);
        try {
            Object list = MAPPER.readValue(body, java.util.Map.class).get("list");
            return list == null ? List.of() : list;
        } catch (Exception e) {
            throw new ServiceHandleException(500, "parse namespace failed: " + e.getMessage(), "解析命名空间失败");
        }
    }

    /**
     * 命名空间资源（对齐 EnterpriseNamespaceResource.get + region_api.list_namespace_resources）：
     * region /v2/cluster/resource?eid&content&namespace → body.bean，再把 unclassified 键移到末尾（对齐 view 的 pop+回填）。
     */
    @SuppressWarnings("unchecked")
    public Object listNamespaceResources(String enterpriseId, String regionId, String content, String namespace) {
        RegionConfig region = regionConfigRepository.findByRegionId(regionId)
                .orElseThrow(() -> ServiceHandleException.notFound("region not found", "数据中心不存在"));
        String path = "/v2/cluster/resource?eid=" + enc(enterpriseId)
                + "&content=" + enc(content == null ? "all" : content)
                + "&namespace=" + enc(namespace);
        String body = regionClient.exchange(region.getRegionName(), "GET", path, null, TimeoutTier.NORMAL, null);
        try {
            Map<String, Object> bean = (Map<String, Object>) MAPPER.readValue(body, Map.class).get("bean");
            if (bean == null) {
                return new LinkedHashMap<>();
            }
            return moveKeyToEnd(bean, "unclassified");
        } catch (ServiceHandleException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceHandleException(500, "parse resource failed: " + e.getMessage(), "解析集群资源失败");
        }
    }

    /** 重建有序 map，把指定键移到末尾（对齐 rainbond data["bean"].pop(k); data["bean"][k]=move）。 */
    private static Map<String, Object> moveKeyToEnd(Map<String, Object> src, String key) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            if (!e.getKey().equals(key)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        if (src.containsKey(key)) {
            out.put(key, src.get(key));
        }
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
