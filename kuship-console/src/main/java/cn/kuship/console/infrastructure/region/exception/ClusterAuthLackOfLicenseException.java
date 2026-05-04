package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/** HTTP 412 + body.msg = {@code "authorize_cluster_lack_of_license"}。集群授权 license 已用满。 */
public class ClusterAuthLackOfLicenseException extends RegionApiException {
    public ClusterAuthLackOfLicenseException(String apiType, String url, String method, Map<String, Object> bean) {
        super(apiType, url, method, 412, 412,
                "authorize_cluster_lack_of_license", "集群授权 license 已用完", bean, null);
    }
}
