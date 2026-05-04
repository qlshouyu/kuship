package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/** HTTP 412 + body.msg = {@code "authorize_cluster_lack_of_node"}。集群授权节点数已用满。 */
public class ClusterAuthLackOfNodeException extends RegionApiException {
    public ClusterAuthLackOfNodeException(String apiType, String url, String method, Map<String, Object> bean) {
        super(apiType, url, method, 412, 412,
                "authorize_cluster_lack_of_node", "集群授权节点数已用满", bean, null);
    }
}
