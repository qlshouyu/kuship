package cn.kuship.console.common.security;

import cn.kuship.console.modules.account.entity.UserInfo;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HS256 JWT 编解码，<b>与 djangorestframework-jwt（PyJWT）逐字节互认</b>。
 *
 * <p>不使用 jjwt：jjwt 强制 HS256 密钥 ≥256 位，而 Django {@code SECRET_KEY} 长度任意（PyJWT 不限制）。
 * 这里直接用 {@code HmacSHA256} 实现，等价于 PyJWT 的签名方式，因此两端互验通过。
 *
 * <p>载荷对齐默认 {@code jwt_payload_handler}：{@code user_id, username, email, nick_name, exp(秒)}，
 * 无 {@code orig_iat}（JWT_ALLOW_REFRESH=False）。
 */
@Service
public class JwtService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER_JSON = "{\"typ\":\"JWT\",\"alg\":\"HS256\"}";

    private final byte[] secret;
    private final long expirationDays;
    private final ObjectMapper objectMapper;

    public JwtService(JwtProperties properties, ObjectMapper objectMapper) {
        this.secret = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.expirationDays = properties.getExpirationDays();
        this.objectMapper = objectMapper;
    }

    /** 为用户签发 token（与 rainbond-console 登录签发等价）。 */
    public String generate(UserInfo user) {
        long exp = Instant.now().plus(expirationDays, ChronoUnit.DAYS).getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", user.getUserId());
        payload.put("username", user.getNickName());
        payload.put("email", user.getEmail());
        payload.put("nick_name", user.getNickName());
        payload.put("exp", exp);

        String header = B64.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String body = B64.encodeToString(objectMapper.writeValueAsBytes(payload));
        String signingInput = header + "." + body;
        String signature = B64.encodeToString(hmac(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    /** 验签并解析，失败抛 {@link JwtVerifyException}。 */
    @SuppressWarnings("unchecked")
    public JwtClaims parse(String token) {
        if (token == null) {
            throw new JwtVerifyException("token is null");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtVerifyException("malformed token");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmac(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] actual;
        try {
            actual = B64D.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtVerifyException("bad signature encoding");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new JwtVerifyException("signature mismatch");
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(B64D.decode(parts[1]), Map.class);
        } catch (RuntimeException e) {
            throw new JwtVerifyException("bad payload: " + e.getMessage());
        }

        Object expVal = payload.get("exp");
        Date expiration = null;
        if (expVal instanceof Number expNum) {
            long expSeconds = expNum.longValue();
            if (Instant.now().getEpochSecond() >= expSeconds) {
                throw new JwtVerifyException("token expired");
            }
            expiration = Date.from(Instant.ofEpochSecond(expSeconds));
        }

        Integer userId = payload.get("user_id") instanceof Number n ? n.intValue() : null;
        return new JwtClaims(
                userId,
                asString(payload.get("username")),
                asString(payload.get("nick_name")),
                asString(payload.get("email")),
                expiration,
                token);
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
