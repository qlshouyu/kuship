package cn.kuship.console.infrastructure.region;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Region API 客户端配置项。
 *
 * <p>关键约定：
 * <ul>
 *   <li>{@link #timeoutSeconds} 与 Python 端 {@code self.timeout=5} 一致</li>
 *   <li>{@link #sslVerify} 默认 {@code false}（与 Python {@code REGION_SSL_VERIFY=false} 一致）；
 *       生产部署应设为 {@code true}</li>
 *   <li>{@link #connectionPoolMaxPerRoute} 为 0 时表示自动 cpu * 5（与 Python {@code cpu_count() * 5} 一致）</li>
 *   <li>{@link #frequentOperationMessages} 用于 HTTP 409 响应 body.msg 命中时抛 {@code RegionApiFrequentException}
 *       而非通用 {@code RegionApiException}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "kuship.region")
public record RegionProperties(
        int timeoutSeconds,
        boolean sslVerify,
        int connectionPoolMaxPerRoute,
        List<String> frequentOperationMessages
) {
    public RegionProperties {
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 5;
        }
        if (frequentOperationMessages == null || frequentOperationMessages.isEmpty()) {
            frequentOperationMessages = List.of(
                    "操作过于频繁，请稍后再试",
                    "wait a moment please",
                    "just wait a moment");
        }
    }

    /**
     * 计算实际的每路由最大连接数：配置 0 时按 cpu_count() * 5 自动算。
     */
    public int effectiveMaxPerRoute() {
        if (connectionPoolMaxPerRoute > 0) {
            return connectionPoolMaxPerRoute;
        }
        return Runtime.getRuntime().availableProcessors() * 5;
    }
}
