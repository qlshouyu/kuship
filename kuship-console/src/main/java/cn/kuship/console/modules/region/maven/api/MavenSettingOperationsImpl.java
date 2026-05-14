package cn.kuship.console.modules.region.maven.api;

import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.appmarket.api.RegionApiSupport;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Primary
public class MavenSettingOperationsImpl implements MavenSettingOperations {

    private static final String API_TYPE = "maven-setting";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public MavenSettingOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Map<String, Object>> listMavenSettings(String enterpriseId, String regionName, boolean onlyName) {
        String url = "/v2/cluster/builder/mavensetting";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId,
                API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        Object bean = processor.extractBean(resp, Map.class, API_TYPE, url, "GET");
        List<Map<String, Object>> raw = List.of();
        if (bean instanceof Map<?, ?> m) {
            Object list = m.get("list");
            if (list instanceof List l) {
                raw = l;
            }
        }
        if (!onlyName) {
            return raw;
        }
        List<Map<String, Object>> projected = new ArrayList<>(raw.size());
        for (Map<String, Object> item : raw) {
            Map<String, Object> simple = new LinkedHashMap<>();
            simple.put("name", item.get("name"));
            simple.put("is_default", item.get("is_default"));
            projected.add(simple);
        }
        return projected;
    }

    @Override
    public Map<String, Object> addMavenSetting(String enterpriseId, String regionName, Map<String, Object> body) {
        String url = "/v2/cluster/builder/mavensetting";
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId,
                API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> getMavenSetting(String enterpriseId, String regionName, String name) {
        String url = "/v2/cluster/builder/mavensetting/" + encode(name);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId,
                API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> updateMavenSetting(String enterpriseId, String regionName, String name,
                                                  Map<String, Object> body) {
        String url = "/v2/cluster/builder/mavensetting/" + encode(name);
        Map<String, Object> safe = body == null ? Map.of() : body;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId,
                API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(safe)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public Map<String, Object> deleteMavenSetting(String enterpriseId, String regionName, String name) {
        String url = "/v2/cluster/builder/mavensetting/" + encode(name);
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId,
                API_TYPE, url, "DELETE",
                c -> c.method(HttpMethod.DELETE).uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "DELETE"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }
}
