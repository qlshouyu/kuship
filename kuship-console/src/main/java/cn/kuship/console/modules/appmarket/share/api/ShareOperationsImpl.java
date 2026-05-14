package cn.kuship.console.modules.appmarket.share.api;

import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.exception.RegionApiSocketException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * {@link ShareOperations} @Primary 实现：7 method 全部 1:1 透传 region API。
 *
 * <p>路径中 {@code namespace} 段统一从 {@link Tenants#getNamespace()} 解析（缺失回退 {@code tenant_name}），
 * 与 helm-release / cluster-extras / third-party 行为一致；唯一例外 {@code getServicePublishStatus}
 * 路径不含 {@code namespace}。
 */
@Service
@Primary
public class ShareOperationsImpl implements ShareOperations {

    private static final String API_TYPE = "share";

    private final RegionClientFactory clientFactory;
    private final RegionApiResponseProcessor processor;
    private final TenantsRepository tenantsRepository;

    public ShareOperationsImpl(RegionClientFactory clientFactory,
                                 RegionApiResponseProcessor processor,
                                 TenantsRepository tenantsRepository) {
        this.clientFactory = clientFactory;
        this.processor = processor;
        this.tenantsRepository = tenantsRepository;
    }

    @Override
    public Map<String, Object> shareCloudService(String regionName, String tenantName, Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(resolveNamespace(tenantName)) + "/cloud-share";
        ResponseEntity<String> resp = exchange(regionName, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> shareService(String regionName, String tenantName, String serviceAlias,
                                              Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(resolveNamespace(tenantName))
                + "/services/" + encode(serviceAlias) + "/share";
        ResponseEntity<String> resp = exchange(regionName, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> getShareServiceResult(String regionName, String tenantName, String serviceAlias,
                                                       String regionShareId) {
        String url = "/v2/tenants/" + encode(resolveNamespace(tenantName))
                + "/services/" + encode(serviceAlias) + "/share/" + encode(regionShareId);
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> sharePlugin(String regionName, String tenantName, String pluginId,
                                             Map<String, Object> body) {
        String url = "/v2/tenants/" + encode(resolveNamespace(tenantName))
                + "/plugins/" + encode(pluginId) + "/share";
        ResponseEntity<String> resp = exchange(regionName, "POST", url,
                c -> c.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? Map.of() : body)
                        .exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "POST"));
    }

    @Override
    public Map<String, Object> getSharePluginResult(String regionName, String tenantName, String pluginId,
                                                      String regionShareId) {
        String url = "/v2/tenants/" + encode(resolveNamespace(tenantName))
                + "/plugins/" + encode(pluginId) + "/share/" + encode(regionShareId);
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public Map<String, Object> getServicePublishStatus(String regionName, String tenantName,
                                                         String serviceKey, String appVersion) {
        String url = "/v2/builder/publish/service/" + encode(serviceKey) + "/version/" + encode(appVersion);
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        return safeBean(processor.extractBean(resp, Map.class, API_TYPE, url, "GET"));
    }

    @Override
    public List<Object> listAppReleases(String regionName, String tenantName, String regionAppId) {
        String url = "/v2/tenants/" + encode(resolveNamespace(tenantName))
                + "/apps/" + encode(regionAppId) + "/releases";
        ResponseEntity<String> resp = exchange(regionName, "GET", url,
                c -> c.get().uri(url).exchange((req, r) -> readResponse(r)));
        JsonNode root = processor.checkStatus(resp, API_TYPE, url, "GET");
        return extractList(root);
    }

    private static List<Object> extractList(JsonNode root) {
        if (root == null) return List.of();
        JsonNode listNode = root.path("list");
        if (listNode.isMissingNode() || listNode.isNull()) {
            listNode = root.path("data").path("list");
        }
        if (listNode.isMissingNode() || listNode.isNull()) {
            return List.of();
        }
        if (!listNode.isArray()) {
            return List.of();
        }
        List<Object> out = new ArrayList<>();
        Iterator<JsonNode> it = listNode.iterator();
        while (it.hasNext()) {
            JsonNode n = it.next();
            if (n.isTextual()) {
                out.add(n.asText());
            } else if (n.isInt() || n.isLong()) {
                out.add(n.asLong());
            } else if (n.isBoolean()) {
                out.add(n.asBoolean());
            } else {
                out.add(n);
            }
        }
        return out;
    }

    private String resolveNamespace(String tenantName) {
        if (tenantName == null) return "";
        return tenantsRepository.findByTenantName(tenantName)
                .map(t -> (t.getNamespace() != null && !t.getNamespace().isBlank())
                        ? t.getNamespace() : t.getTenantName())
                .orElse(tenantName);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> safeBean(Object obj) {
        return obj == null ? Map.of() : (Map) obj;
    }

    private ResponseEntity<String> exchange(String regionName, String httpMethod, String url,
                                              Function<RestClient, ResponseEntity<String>> caller) {
        RegionClient regionClient = clientFactory.getClient(regionName, "");
        try {
            return caller.apply(regionClient.restClient());
        } catch (ResourceAccessException socketErr) {
            try {
                return caller.apply(regionClient.restClient());
            } catch (ResourceAccessException retryErr) {
                throw new RegionApiSocketException(API_TYPE, url, httpMethod, retryErr);
            }
        }
    }

    private static ResponseEntity<String> readResponse(org.springframework.http.client.ClientHttpResponse r) {
        try {
            return ResponseEntity.status(r.getStatusCode())
                    .headers(r.getHeaders())
                    .body(new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
