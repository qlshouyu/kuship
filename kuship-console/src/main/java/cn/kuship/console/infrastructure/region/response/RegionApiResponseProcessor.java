package cn.kuship.console.infrastructure.region.response;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher;
import cn.kuship.console.infrastructure.region.exception.ClusterAuthLackOfLicenseException;
import cn.kuship.console.infrastructure.region.exception.ClusterAuthLackOfLicenseExpireException;
import cn.kuship.console.infrastructure.region.exception.ClusterAuthLackOfMemoryException;
import cn.kuship.console.infrastructure.region.exception.ClusterAuthLackOfNodeException;
import cn.kuship.console.infrastructure.region.exception.ClusterLackOfMemoryException;
import cn.kuship.console.infrastructure.region.exception.InvalidLicenseException;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.exception.RegionApiFrequentException;
import cn.kuship.console.infrastructure.region.exception.TenantLackOfCpuException;
import cn.kuship.console.infrastructure.region.exception.TenantLackOfMemoryException;
import cn.kuship.console.infrastructure.region.exception.TenantQuotaCpuLackException;
import cn.kuship.console.infrastructure.region.exception.TenantQuotaMemoryLackException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 解析 Rainbond Go 集群 HTTP 响应：成功反序列化为强类型 DTO；失败按
 * rainbond-console {@code regionapibaseclient.py:_check_status} 的逻辑映射为对应异常。
 *
 * <p>HTTP 状态码与业务 {@code code} 解耦：异常对象内部 {@code httpStatus} 仅供调试，
 * 对外响应体的 {@code code} 走 region 响应 body 的 {@code code} 字段。
 */
@Component
public class RegionApiResponseProcessor {

    private final ObjectMapper json;
    private final RegionProperties properties;
    private final RegionErrorMsgEnricher enricher;

    public RegionApiResponseProcessor(ObjectMapper json,
                                       RegionProperties properties,
                                       RegionErrorMsgEnricher enricher) {
        this.json = json;
        this.properties = properties;
        this.enricher = enricher;
    }

    /**
     * 把响应体 {@code data.bean} 反序列化为 {@code beanType}；失败按错误码映射抛对应异常。
     */
    public <T> T extractBean(ResponseEntity<String> response,
                             Class<T> beanType,
                             String apiType, String url, String httpMethod) {
        JsonNode body = checkStatus(response, apiType, url, httpMethod);
        JsonNode beanNode = body.path("data").path("bean");
        if (beanNode.isMissingNode() || beanNode.isNull()) {
            return null;
        }
        return json.convertValue(beanNode, beanType);
    }

    /**
     * 把响应体 {@code data.list} 反序列化为 {@code List<elementType>}。
     */
    public <T> List<T> extractList(ResponseEntity<String> response,
                                    Class<T> elementType,
                                    String apiType, String url, String httpMethod) {
        JsonNode body = checkStatus(response, apiType, url, httpMethod);
        JsonNode listNode = body.path("data").path("list");
        if (listNode.isMissingNode() || listNode.isNull() || !listNode.isArray()) {
            return List.of();
        }
        return json.convertValue(listNode,
                json.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    /**
     * 公开的状态校验入口（暴露 JSON 体便于业务 method 自取 `data.bean` 自定义路径）。
     */
    public JsonNode checkStatus(ResponseEntity<String> response,
                                 String apiType, String url, String httpMethod) {
        int status = response.getStatusCode().value();
        String rawBody = response.getBody();

        // HTTP 200 但空 body / 非 JSON → 错误（与 Python 一致）
        if (status >= 200 && status < 400) {
            if (rawBody == null || rawBody.isBlank()) {
                throw new RegionApiException(apiType, url, httpMethod, status, status,
                        "request region api body is nil", "集群请求网络异常",
                        Map.of(), null);
            }
            try {
                return json.readTree(rawBody);
            } catch (Exception e) {
                throw new RegionApiException(apiType, url, httpMethod, status, status,
                        "request region api body is not valid json", "集群请求网络异常",
                        Map.of(), e);
            }
        }

        // 4xx / 5xx
        JsonNode body = parseOrEmpty(rawBody);
        Map<String, Object> beanMap = extractBeanAsMap(body);

        // HTTP 401 + bean.code = 10400 → InvalidLicense
        if (status == 401) {
            JsonNode beanCode = body.path("data").path("bean").path("code");
            if (beanCode.isInt() && beanCode.intValue() == 10400) {
                throw new InvalidLicenseException(apiType, url, httpMethod,
                        body.path("data").path("bean").path("msg").asText(""),
                        beanMap);
            }
        }

        // HTTP 412 + 字面错误码（9 种）
        if (status == 412) {
            String msg = body.path("msg").asText("");
            switch (msg) {
                case "cluster_lack_of_memory" -> throw new ClusterLackOfMemoryException(apiType, url, httpMethod, beanMap);
                case "tenant_lack_of_memory" -> throw new TenantLackOfMemoryException(apiType, url, httpMethod, beanMap);
                case "tenant_lack_of_cpu" -> throw new TenantLackOfCpuException(apiType, url, httpMethod, beanMap);
                case "tenant_quota_cpu_lack" -> throw new TenantQuotaCpuLackException(apiType, url, httpMethod, beanMap);
                case "tenant_quota_memory_lack" -> throw new TenantQuotaMemoryLackException(apiType, url, httpMethod, beanMap);
                case "authorize_cluster_lack_of_memory" -> throw new ClusterAuthLackOfMemoryException(apiType, url, httpMethod, beanMap);
                case "authorize_cluster_lack_of_node" -> throw new ClusterAuthLackOfNodeException(apiType, url, httpMethod, beanMap);
                case "authorize_cluster_lack_of_license" -> throw new ClusterAuthLackOfLicenseException(apiType, url, httpMethod, beanMap);
                case "authorize_expiration_of_authorization" -> throw new ClusterAuthLackOfLicenseExpireException(apiType, url, httpMethod, beanMap);
                default -> {
                    // fall through to generic
                }
            }
        }

        // HTTP 409 + body.msg 在频繁操作短语集合内 → RegionApiFrequent
        if (status == 409) {
            String msg = body.path("msg").asText("");
            if (msg != null && !msg.isBlank() && isFrequentOperation(msg)) {
                throw new RegionApiFrequentException(apiType, url, httpMethod, status, msg, beanMap);
            }
        }

        // 通用：4xx-5xx + body 含 code → RegionApiException
        JsonNode codeNode = body.path("code");
        if (!codeNode.isMissingNode() && !codeNode.isNull()) {
            int bizCode = codeNode.asInt(status);
            String msg = body.path("msg").asText("");
            // 优先使用 region 响应自带的 msg_show（Go 后端已汉化），仅在缺失时由 enricher 兜底
            String upstreamMsgShow = body.path("msg_show").asText("");
            String msgShow = (upstreamMsgShow != null && !upstreamMsgShow.isBlank())
                    ? upstreamMsgShow
                    : enricher.enrich(msg);
            throw new RegionApiException(apiType, url, httpMethod, status,
                    bizCode, msg, msgShow, beanMap, null);
        }

        // 兜底：body 为空或无 code
        throw new RegionApiException(apiType, url, httpMethod, status, status,
                "request region api body is nil", "集群请求网络异常", beanMap, null);
    }

    private boolean isFrequentOperation(String msg) {
        String trimmed = msg.trim().toLowerCase(Locale.ROOT);
        for (String phrase : properties.frequentOperationMessages()) {
            if (trimmed.equalsIgnoreCase(phrase.trim())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode parseOrEmpty(String body) {
        if (body == null || body.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(body);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractBeanAsMap(JsonNode body) {
        JsonNode bean = body.path("data").path("bean");
        if (bean.isMissingNode() || bean.isNull() || !bean.isObject()) {
            return Map.of();
        }
        return json.convertValue(bean, Map.class);
    }
}
