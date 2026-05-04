package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/** HTTP 412 + body.msg = {@code "tenant_lack_of_cpu"}。团队 CPU 配额不足。 */
public class TenantLackOfCpuException extends RegionApiException {
    public TenantLackOfCpuException(String apiType, String url, String method, Map<String, Object> bean) {
        super(apiType, url, method, 412, 412, "tenant_lack_of_cpu", "团队 CPU 配额不足", bean, null);
    }
}
