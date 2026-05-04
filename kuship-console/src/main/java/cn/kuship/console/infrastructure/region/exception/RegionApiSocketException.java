package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/**
 * Socket 类异常重试后仍失败。对应 Python {@code RegionApiBaseHttpClient.ApiSocketError}。
 */
public class RegionApiSocketException extends RegionApiException {

    public RegionApiSocketException(String apiType, String url, String method, Throwable cause) {
        super(apiType, url, method, 0, 503,
                cause != null ? cause.getMessage() : "socket error",
                "集群网络不可达", Map.of(), cause);
    }
}
