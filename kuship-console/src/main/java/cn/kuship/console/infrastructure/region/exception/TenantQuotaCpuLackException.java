package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/** HTTP 412 + body.msg = {@code "tenant_quota_cpu_lack"}。团队 CPU 配额限制。 */
public class TenantQuotaCpuLackException extends RegionApiException {
    public TenantQuotaCpuLackException(String apiType, String url, String method, Map<String, Object> bean) {
        super(apiType, url, method, 412, 412, "tenant_quota_cpu_lack", "团队 CPU 配额已用满", bean, null);
    }
}
