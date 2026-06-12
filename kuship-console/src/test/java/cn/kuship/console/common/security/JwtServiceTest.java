package cn.kuship.console.common.security;

import cn.kuship.console.modules.account.entity.UserInfo;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JWT 签发/验签往返与 Django 风格 claims 校验。 */
class JwtServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JwtService service(String secret) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret);
        return new JwtService(p, objectMapper);
    }

    private UserInfo user() {
        UserInfo u = new UserInfo();
        u.setUserId(42);
        u.setNickName("admin");
        u.setEmail("admin@goodrain.com");
        return u;
    }

    @Test
    void sign_then_parse_round_trip_with_django_style_claims() {
        JwtService svc = service("shared-secret-key-with-rainbond");
        String token = svc.generate(user());

        JwtClaims claims = svc.parse(token);
        assertThat(claims.userId()).isEqualTo(42);
        assertThat(claims.username()).isEqualTo("admin");
        // 实测对齐 7070：token 载荷为 {user_id, username, exp, email}，不含 nick_name → 解析为 null
        assertThat(claims.nickName()).isNull();
        assertThat(claims.email()).isEqualTo("admin@goodrain.com");
        assertThat(claims.expiration()).isNotNull();
        assertThat(claims.rawToken()).isEqualTo(token);
    }

    @Test
    void parse_rejects_token_signed_with_different_secret() {
        String token = service("secret-A").generate(user());
        JwtService other = service("secret-B");
        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtVerifyException.class);
    }
}
