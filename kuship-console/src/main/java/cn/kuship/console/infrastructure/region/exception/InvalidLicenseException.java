package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/**
 * HTTP 401 + {@code data.bean.code = 10400}。
 * 对应 Python {@code RegionApiBaseHttpClient.InvalidLicenseError}。
 */
public class InvalidLicenseException extends RegionApiException {

    public InvalidLicenseException(String apiType, String url, String method,
                                   String msg, Map<String, Object> bean) {
        super(apiType, url, method, 401, 10400, msg, "集群授权失效或未授权", bean, null);
    }
}
