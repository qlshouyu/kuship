package cn.kuship.console.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link PasswordEncryptor} 与 rainbond-console {@code encrypt_passwd} 逐字节一致
 * （期望值由参考实现 www/utils/crypt.py 计算得出）。
 */
class PasswordEncryptorTest {

    @Test
    void encrypt_matches_reference_python_output() {
        // encrypt_passwd("admin@goodrain.com" + "admin1234") == 15ec44b9de1adc2e
        assertThat(PasswordEncryptor.encrypt("admin@goodrain.comadmin1234")).isEqualTo("15ec44b9de1adc2e");
        // encrypt_passwd("dev@kuship.cn" + "Passw0rd!") == 0d874ed84e8a9a0d
        assertThat(PasswordEncryptor.encrypt("dev@kuship.cnPassw0rd!")).isEqualTo("0d874ed84e8a9a0d");
    }

    @Test
    void matches_uses_email_plus_password() {
        String email = "admin@goodrain.com";
        String stored = "15ec44b9de1adc2e";
        assertThat(PasswordEncryptor.matches(email, "admin1234", stored)).isTrue();
        assertThat(PasswordEncryptor.matches(email, "wrong-password", stored)).isFalse();
        assertThat(PasswordEncryptor.matches(email, "admin1234", null)).isFalse();
    }
}
