package cn.kuship.console.modules.account.jwt;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtProperties;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.modules.account.entity.UserInfo;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 用 rainbond djangorestframework-jwt 兼容的 payload 形状签发 token，默认 TTL 来自 {@link JwtProperties#expirationDays()}。 */
@Component
public class JwtIssuer {

    private final JwtTokenService tokenService;
    private final JwtProperties properties;

    public JwtIssuer(JwtTokenService tokenService, JwtProperties properties) {
        this.tokenService = tokenService;
        this.properties = properties;
    }

    public String issue(UserInfo user) {
        JwtClaims claims = new JwtClaims(
                user.getUserId() == null ? null : user.getUserId().longValue(),
                user.getNickName(),
                user.getEmail(),
                null,
                null,
                java.util.Map.of());
        return tokenService.encode(claims, Duration.ofDays(properties.expirationDays()));
    }
}
