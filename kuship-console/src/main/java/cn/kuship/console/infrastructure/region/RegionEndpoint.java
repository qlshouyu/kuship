package cn.kuship.console.infrastructure.region;

import cn.kuship.console.modules.region.entity.RegionConfig;

/**
 * 从 {@code region_info} 解析出的连接信息（地址 + 凭证）。
 */
public record RegionEndpoint(
        String regionName,
        String url,
        String token,
        String sslCaCert,
        String certFile,
        String keyFile
) {
    public static RegionEndpoint from(RegionConfig c) {
        return new RegionEndpoint(
                c.getRegionName(), c.getUrl(), c.getToken(),
                c.getSslCaCert(), c.getCertFile(), c.getKeyFile());
    }

    public boolean hasMutualTls() {
        return certFile != null && !certFile.isBlank() && keyFile != null && !keyFile.isBlank();
    }
}
