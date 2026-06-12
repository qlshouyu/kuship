package cn.kuship.console.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Redis 会话黑名单（首版必需）。token 无 jti/orig_iat，故以 sha256(token) 为键。
 * 登出/强制下线写入黑名单，TTL 对齐 token 剩余 exp；命中即视为失效。
 */
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "kuship:jwt:blacklist:";

    private final StringRedisTemplate redis;

    public TokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void blacklist(String token, Date expiration) {
        long ttlSeconds = expiration == null ? 0
                : Duration.between(Instant.now(), expiration.toInstant()).getSeconds();
        if (ttlSeconds <= 0) {
            return; // 已过期，无需写入
        }
        redis.opsForValue().set(key(token), "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redis.hasKey(key(token)));
    }

    private String key(String token) {
        return KEY_PREFIX + sha256(token);
    }

    private static String sha256(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
