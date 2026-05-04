package cn.kuship.console.infrastructure.region.exception;

import java.util.Map;

/**
 * HTTP 409 + body.msg 匹配 {@code kuship.region.frequent-operation-messages} 时抛出。
 * 表示 region 端因频率限制拒绝服务，调用方应稍后重试（不在客户端自动重试）。
 *
 * <p>对应 Python {@code RegionApiBaseHttpClient.CallApiFrequentError}。
 */
public class RegionApiFrequentException extends RegionApiException {

    public RegionApiFrequentException(String apiType, String url, String method,
                                      int httpStatus, String msg, Map<String, Object> bean) {
        super(apiType, url, method, httpStatus, 429, msg, "操作过于频繁，请稍后再试", bean, null);
    }
}
