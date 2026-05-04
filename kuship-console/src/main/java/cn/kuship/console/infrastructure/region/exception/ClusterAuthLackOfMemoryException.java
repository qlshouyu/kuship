package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/** HTTP 412 + body.msg = {@code "authorize_cluster_lack_of_memory"}。集群授权剩余内存不足。 */
public class ClusterAuthLackOfMemoryException extends RegionApiException {
    public ClusterAuthLackOfMemoryException(String apiType, String url, String method, Map<String, Object> bean) {
        super(apiType, url, method, 412, 412,
                "authorize_cluster_lack_of_memory", "集群授权剩余内存不足", bean, null);
    }
}
