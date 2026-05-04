package cn.kuship.console.common.security;

import java.time.Instant;
import java.util.Map;

/**
 * 从 JWT payload 解析出的关键 claims。
 *
 * <p>字段命名严格对齐 djangorestframework-jwt 1.11.0 的 {@code jwt_payload_handler}：
 * {@code user_id}、{@code username}（rainbond-console 中字段名为 {@code nick_name}，但 jwt 库统一注入 {@code username}）、
 * {@code email}、{@code exp}、{@code orig_iat}。
 *
 * <p>{@link #raw} 保留原始 claims map，便于业务层访问其他自定义字段。
 */
public record JwtClaims(
        Long userId,
        String username,
        String email,
        Instant issuedAt,
        Instant expiresAt,
        Map<String, Object> raw
) {
}
