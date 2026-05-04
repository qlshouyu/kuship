package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/** HTTP 412 + body.msg = {@code "tenant_quota_memory_lack"}。团队内存配额限制。 */
public class TenantQuotaMemoryLackException extends RegionApiException {
    public TenantQuotaMemoryLackException(String apiType, String url, String method, Map<String, Object> bean) {
        super(apiType, url, method, 412, 412, "tenant_quota_memory_lack", "团队内存配额已用满", bean, null);
    }
}
