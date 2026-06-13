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
import java.util.List;

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

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
