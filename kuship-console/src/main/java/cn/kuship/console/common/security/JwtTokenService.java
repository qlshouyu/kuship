package cn.kuship.console.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * JWT 编解码服务。兼容 rainbond-console（djangorestframework-jwt 1.11.0）签发的 token。
 *
 * <ul>
 *   <li>算法：HS256（与 Django 配置一致）</li>
 *   <li>签名密钥：{@link JwtProperties#secretKey()} —— 必须与 Django 端 {@code SECRET_KEY} 同源，
 *       通过 {@code JWT_SECRET_KEY} 环境变量注入</li>
 *   <li>过期校验：启用，{@link JwtProperties#leewaySeconds()} 控制时钟偏移容忍</li>
 *   <li>非 local profile 启动时若 secret-key 为空 → {@link IllegalStateException}（拒绝启动）</li>
 * </ul>
 *
 * <p>{@link #encode(JwtClaims, Duration)} 仅供测试与后续业务 change（如登录端点）使用，本 change 不暴露登录路径。
 */
@Service
public class JwtTokenService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final JwtProperties properties;
    private final Environment environment;
    private SecretKey signingKey;

    public JwtTokenService(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String secret = properties.secretKey();
        Set<String> profiles = Set.of(environment.getActiveProfiles());
        boolean isLocal = profiles.contains("local");

        if (secret == null || secret.isBlank()) {
            if (!isLocal) {
                throw new IllegalStateException(
                        "JWT_SECRET_KEY must be set in non-local profiles "
                                + "(active profiles: " + profiles + ")");
            }
            log.warn("JWT secret-key is empty under local profile; using a derived dev key. "
                    + "DO NOT use this configuration in production.");
            secret = "kuship-local-dev-fallback-key-do-not-use-in-prod-please";
        }

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 要求 key 至少 256 bit
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            for (int i = bytes.length; i < 32; i++) {
                padded[i] = '0';
            }
            bytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * 解码并校验 JWT token。
     *
     * @throws JwtException 签名错误 / 过期 / 格式不合法
     */
    public JwtClaims decode(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .clockSkewSeconds(properties.leewaySeconds())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = claims.get("user_id") instanceof Number n ? n.longValue() : null;
        String username = stringOrNull(claims.get("username"));
        if (username == null) {
            // rainbond-console 用户表里有 nick_name 字段
            username = stringOrNull(claims.get("nick_name"));
        }
        String email = stringOrNull(claims.get("email"));
        Instant issuedAt = claims.get("orig_iat") instanceof Number n ? Instant.ofEpochSecond(n.longValue()) : null;
        Instant expiresAt = claims.getExpiration() != null ? claims.getExpiration().toInstant() : null;

        Map<String, Object> raw = new HashMap<>(claims);
        return new JwtClaims(userId, username, email, issuedAt, expiresAt, raw);
    }

    /**
     * 测试与登录端点用：用 djangorestframework-jwt 兼容的 payload 形状签发 token。
     */
    public String encode(JwtClaims claims, Duration ttl) {
        Map<String, Object> payload = new HashMap<>();
        if (claims.userId() != null) payload.put("user_id", claims.userId());
        if (claims.username() != null) payload.put("username", claims.username());
        if (claims.email() != null) payload.put("email", claims.email());
        long nowSec = Instant.now().getEpochSecond();
        payload.put("orig_iat", nowSec);

        return Jwts.builder()
                .claims(payload)
                .issuedAt(new Date(nowSec * 1000))
                .expiration(Date.from(Instant.now().plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : o.toString();
    }
}
