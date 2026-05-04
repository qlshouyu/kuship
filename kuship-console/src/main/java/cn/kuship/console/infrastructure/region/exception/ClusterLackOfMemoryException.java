package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/** HTTP 412 + body.msg = {@code "cluster_lack_of_memory"}。集群内存不足。 */
public class ClusterLackOfMemoryException extends RegionApiException {
    public ClusterLackOfMemoryException(String apiType, String url, String method, Map<String, Object> bean) {
        super(apiType, url, method, 412, 412, "cluster_lack_of_memory", "集群内存不足", bean, null);
    }
}
