package cn.kuship.console.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * JWT 配置。{@code secret} 必须与 rainbond-console 的环境变量 {@code SECRET_KEY} 同源（drf-jwt JWT_SECRET_KEY）。
 */
@ConfigurationProperties(prefix = "kuship.jwt")
public class JwtProperties {

    /** 同源密钥；非 local profile 下为空将拒绝启动（见 JwtSecretValidator）。 */
    private String secret = "";

    /** 接受的 Authorization 前缀（大小写不敏感），主前缀 GRJWT。 */
    private List<String> headerPrefixes = List.of("GRJWT", "jwt", "Bearer");

    /** drf-jwt 的 JWT_AUTH_COOKIE，名为 token。 */
    private String cookieName = "token";

    /** token 有效期（天），对齐 drf-jwt 的 10 年。 */
    private long expirationDays = 3650;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public List<String> getHeaderPrefixes() {
        return headerPrefixes;
    }

    public void setHeaderPrefixes(List<String> headerPrefixes) {
        this.headerPrefixes = headerPrefixes;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public long getExpirationDays() {
        return expirationDays;
    }

    public void setExpirationDays(long expirationDays) {
        this.expirationDays = expirationDays;
    }
}
