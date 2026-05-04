package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/** HTTP 412 + body.msg = {@code "authorize_expiration_of_authorization"}。集群授权 license 已过期。 */
public class ClusterAuthLackOfLicenseExpireException extends RegionApiException {
    public ClusterAuthLackOfLicenseExpireException(String apiType, String url, String method,
                                                   Map<String, Object> bean) {
        super(apiType, url, method, 412, 412,
                "authorize_expiration_of_authorization", "集群授权已过期", bean, null);
    }
}
