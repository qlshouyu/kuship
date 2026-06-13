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
import java.util.Map;

/** 集群 CNB 框架读（对齐 EnterpriseRegionCNBFrameworks + region_api.get_cnb_frameworks）。 */
@Service
public class RegionCnbService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RegionConfigRepository regionConfigRepository;
    private final RegionClient regionClient;

    public RegionCnbService(RegionConfigRepository regionConfigRepository, RegionClient regionClient) {
        this.regionConfigRepository = regionConfigRepository;
        this.regionClient = regionClient;
    }

    /** region /v2/cluster/cnb/frameworks?lang → body.list（CNB 框架定义列表，缺省 []）。 */
    @SuppressWarnings("unchecked")
    public List<Object> showFrameworks(String enterpriseId, String regionId, String lang) {
        RegionConfig region = regionConfigRepository.findByRegionId(regionId)
                .orElseThrow(() -> ServiceHandleException.notFound("region not found", "数据中心不存在"));
        String path = "/v2/cluster/cnb/frameworks?lang=" + enc(lang == null || lang.isEmpty() ? "nodejs" : lang);
        String body = regionClient.exchange(region.getRegionName(), "GET", path, null, TimeoutTier.NORMAL, null);
        try {
            Object list = MAPPER.readValue(body, Map.class).get("list");
            return list == null ? List.of() : (List<Object>) list;
        } catch (Exception e) {
            throw new ServiceHandleException(500, "parse cnb frameworks failed: " + e.getMessage(), "解析CNB框架失败");
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
