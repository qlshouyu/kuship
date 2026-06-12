package cn.kuship.console.common.security;

import java.util.Date;

/**
 * 解析后的 JWT 载荷（Django 风格 claims，不做名字转换）。
 * 实测 {@code JWT_ALLOW_REFRESH=False}，无 {@code orig_iat}。
 */
public record JwtClaims(
        Integer userId,
        String username,
        String nickName,
        String email,
        Date expiration,
        String rawToken
) {
}
