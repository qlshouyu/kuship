package cn.kuship.console.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * JWT 配置项。
 *
 * <p>{@code secret-key} 必须与 rainbond-console（Django）侧的 {@code SECRET_KEY} 同源，
 * 通过 {@code JWT_SECRET_KEY} 环境变量注入；非 local profile 启动时若为空将拒绝启动。
 *
 * <p>{@code authHeaderPrefixes} 决定 {@code Authorization} 头可接受的前缀（不区分大小写）。
 * rainbond-console 实际接受 {@code GRJWT}（主）与 {@code jwt}（外部 portal 兼容）两种。
 *
 * <p>{@code expirationDays} 是 {@code JwtIssuer} 默认签发的过期天数。rainbond-console 历史选择 3650 天
 * （≈10 年，永久），生产部署时应下调。
 */
@ConfigurationProperties(prefix = "kuship.security.jwt")
public record JwtProperties(
        String secretKey,
        List<String> authHeaderPrefixes,
        String algorithm,
        long leewaySeconds,
        long expirationDays
) {
    public JwtProperties {
        if (authHeaderPrefixes == null || authHeaderPrefixes.isEmpty()) {
            authHeaderPrefixes = List.of("GRJWT", "jwt");
        }
        if (algorithm == null || algorithm.isBlank()) {
            algorithm = "HS256";
        }
        if (expirationDays <= 0) {
            expirationDays = 3650;
        }
    }
}
