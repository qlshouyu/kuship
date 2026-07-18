package cn.kuship.console.modules.region.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prometheus 即时查询代理（对齐 rainbond MonitorQueryView → region_api.get_query_data）。
 * 把 query 透传到 region /api/v1/query，原样返回 Prometheus 响应（裸 JSON，不走信封）。
 * 注：本环境 region 不可达 → 降级返回空向量 {status:"success",data:{resultType:"vector",result:[]}}，
 * 与 7070 在该 promQL 无数据时的实测出参一致。
 */
@Service
public class MonitorQueryService {

    private static final Logger log = LoggerFactory.getLogger(MonitorQueryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RegionClient regionClient;

    public MonitorQueryService(RegionClient regionClient) {
        this.regionClient = regionClient;
    }

    /** 透传 Prometheus /api/v1/query 响应；失败降级为空向量。 */
    public Object query(String regionName, String query) {
        try {
            String encoded = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8);
            String body = regionClient.exchange(regionName, "GET",
                    "/api/v1/query?query=" + encoded, null, TimeoutTier.NORMAL, null);
            return MAPPER.readValue(body, Object.class);
        } catch (Exception e) {
            log.debug("monitor query on region {}: {}", regionName, e.getMessage());
            return emptyVector();
        }
    }

    private static Map<String, Object> emptyVector() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resultType", "vector");
        data.put("result", List.of());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "success");
        out.put("data", data);
        return out;
    }
}
