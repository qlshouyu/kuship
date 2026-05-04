package cn.kuship.console.modules.account.password;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 与 rainbond Python 端 {@code www/utils/crypt.py::encrypt_passwd} 输出严格一致。
 *
 * <p>fixture 由 Python 端实算得到（参见本类 javadoc 顶部注释），任何修改都会导致跨服务登录失败。
 */
class LegacyPasswordEncoderTest {

    private final LegacyPasswordEncoder encoder = new LegacyPasswordEncoder();

    @ParameterizedTest
    @CsvSource({
            "alice@example.com, goodrain,     e8f78a5a15d4effc",
            "admin@example.com, password,     30f536e3ba4c90c0",
            "bob@test.io,       kuship12,     e9fd833f9e06992c",
            "user@a.b,          longpassword, e38f9b406a9bcf71",
            "a@b.com,           mypassword,   dd15ea0f9c80407a"
    })
    void encode_matchesPythonFixture(String email, String rawPassword, String expected) {
        String input = email + rawPassword;
        assertEquals(expected, encoder.encode(input));
    }

    @Test
    void matches_returnsTrueForCorrectPassword() {
        String input = "alice@example.comgoodrain";
        String hashed = encoder.encode(input);
        assertTrue(encoder.matches(input, hashed));
    }

    @Test
    void matches_returnsFalseForWrongPassword() {
        String correct = encoder.encode("alice@example.comgoodrain");
        assertFalse(encoder.matches("alice@example.comWRONGPASSWORD", correct));
    }

    @Test
    void encode_throwsForShortInput() {
        // 长度 < 8 (charAt(7) 越界)
        assertThrows(IllegalArgumentException.class, () -> encoder.encode("short"));
    }

    @Test
    void encode_throwsForNullInput() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(null));
    }

    @Test
    void matches_returnsFalseForNullEncoded() {
        assertFalse(encoder.matches("alice@example.comgoodrain", null));
    }
}
