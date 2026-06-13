package cn.kuship.console.infrastructure.region;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RegionClient 配置。{@code sslVerify} 对齐 {@code REGION_SSL_VERIFY}，默认 false。
 */
@ConfigurationProperties(prefix = "kuship.region")
public class RegionProperties {

    /** 是否校验下游 region-api TLS 证书，默认 false（与 rainbond-console 一致）。 */
    private boolean sslVerify = false;

    /** 默认重试次数。 */
    private int retryCount = 2;

    /** region-api 基地址覆盖（开发环境 region_info.url 为集群内 DNS 不可达时用，如 https://localhost:8443）。 */
    private String urlOverride = "";

    public String getUrlOverride() {
        return urlOverride;
    }

    public void setUrlOverride(String urlOverride) {
        this.urlOverride = urlOverride;
    }

    public boolean isSslVerify() {
        return sslVerify;
    }

    public void setSslVerify(boolean sslVerify) {
        this.sslVerify = sslVerify;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
}
