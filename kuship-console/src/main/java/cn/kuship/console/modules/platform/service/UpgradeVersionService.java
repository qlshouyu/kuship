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
                return List.of();
            }
            List<Map<String, Object>> data = MAPPER.readValue(resp.body(), new TypeReference<>() {
            });
            return data.stream()
                    .map(m -> m.get("version"))
                    .filter(v -> v != null)
                    .map(Object::toString)
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("fetch upgrade versions failed: {}", e.getMessage());
            return List.of();
        }
    }
}
