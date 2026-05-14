package cn.kuship.console.modules.application.api;

import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Primary
public class LangVersionOperationsImpl implements LangVersionOperations {

    private static final String API_TYPE = "lang-version";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;

    public LangVersionOperationsImpl(RegionClientFactory clientFactory, RegionApiResponseProcessor processor) {
        this.clientFactory = clientFactory;
        this.processor = processor;
    }

    @Override
    public Map<String, Object> getLangVersion(String enterpriseId, String regionName, String lang,
                                                String show, String buildStrategy) {
        StringBuilder qs = new StringBuilder();
        qs.append("?language=").append(encode(safe(lang)));
        qs.append("&show=").append(encode(safe(show)));
        if (StringUtils.hasText(buildStrategy)) {
            qs.append("&build_strategy=").append(encode(buildStrategy));
        }
        String url = "/v2/cluster/langVersion" + qs;
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> createLangVersion(String enterpriseId, String regionName, Map<String, Object> body) {
        String url = "/v2/cluster/langVersion";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "POST",
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> updateLangVersion(String enterpriseId, String regionName, Map<String, Object> body) {
        String url = "/v2/cluster/langVersion";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "PUT",
                c -> c.put().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "PUT"));
    }

    @Override
    public Map<String, Object> deleteLangVersion(String enterpriseId, String regionName, Map<String, Object> body) {
        String url = "/v2/cluster/langVersion";
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "DELETE",
                c -> c.method(HttpMethod.DELETE).uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "DELETE"));
    }

    @Override
    public Map<String, Object> getCnbFrameworks(String enterpriseId, String regionName, String lang) {
        String url = "/v2/cluster/cnb/frameworks?lang=" + encode(safe(lang));
        ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, enterpriseId, API_TYPE, url, "GET",
                c -> c.get().uri(url).exchange((req, r) -> RegionApiSupport.readAsString(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }
}
