package cn.kuship.console.modules.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 平台可升级版本读（对齐 rainbond UpgradeVersionLView.get + fetch_json_data）。
 * 从 VERSION_INFO_URL 拉取版本清单 JSON，取 version 字段降序排序；任何异常/非 200 → 返回空数组。
 */
@Service
public class UpgradeVersionService {

    private static final Logger log = LoggerFactory.getLogger(UpgradeVersionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_URL = "https://get.rainbond.com/upgrade-versions.json";

    private final String versionInfoUrl =
            System.getenv().getOrDefault("VERSION_INFO_URL", DEFAULT_URL);

    /** 版本号降序列表；失败返回 []（对齐 fetch_json_data 返回 None → JsonResponse([])）。 */
    public List<String> listVersions() {
        List<Map<String, Object>> data = fetchData();
        if (data == null) {
            return List.of();
        }
        return data.stream()
                .map(m -> m.get("version"))
                .filter(v -> v != null)
                .map(Object::toString)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /** 版本详情（对齐 UpgradeVersionRView：无清单或未命中/detail 假值 → {}）。 */
    public Object versionDetail(String version) {
        return fieldOfVersion(version, "detail");
    }

    /** 版本镜像清单（对齐 UpgradeVersionImagesView：同上取 images）。 */
    public Object versionImages(String version) {
        return fieldOfVersion(version, "images");
    }

    /** next((item[field] for item if item.version==version), None) or {} 的 Python 语义。 */
    private Object fieldOfVersion(String version, String field) {
        List<Map<String, Object>> data = fetchData();
        if (data == null) {
            return Map.of();
        }
        Object value = data.stream()
                .filter(m -> version.equals(m.get("version")))
                .map(m -> m.get(field))
                .findFirst()
                .orElse(null);
        boolean falsy = value == null || "".equals(value)
                || (value instanceof Map<?, ?> mv && mv.isEmpty())
                || (value instanceof List<?> lv && lv.isEmpty());
        return falsy ? Map.of() : value;
    }

    /** 对齐 fetch_json_data：拉取 VERSION_INFO_URL；任何异常/非 200 → null。 */
    private List<Map<String, Object>> fetchData() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(versionInfoUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            return MAPPER.readValue(resp.body(), new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("fetch upgrade versions failed: {}", e.getMessage());
            return null;
        }
    }
}
