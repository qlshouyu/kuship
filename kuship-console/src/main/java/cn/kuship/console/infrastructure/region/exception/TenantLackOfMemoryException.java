package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/** HTTP 412 + body.msg = {@code "tenant_lack_of_memory"}。团队内存配额不足。 */
public class TenantLackOfMemoryException extends RegionApiException {
    public TenantLackOfMemoryException(String apiType, String url, String method, Map<String, Object> bean) {
        super(apiType, url, method, 412, 412, "tenant_lack_of_memory", "团队内存配额不足", bean, null);
    }
}
