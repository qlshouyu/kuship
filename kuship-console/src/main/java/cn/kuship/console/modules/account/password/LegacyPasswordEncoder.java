package cn.kuship.console.modules.account.password;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 复刻 rainbond {@code www/utils/crypt.py::encrypt_passwd} 算法，保持与 rainbond-console 数据库
 * 中已有的 {@code user_info.password} 字段二进制兼容（跨服务登录前提）。
 *
 * <pre>
 * input  = email + rawPassword          // length 必须 ≥ 8
 * word   = ord(input[7]) + input + ord(input[5]) + "goodrain" + (ord(input[2]) / 7)
 * hash   = SHA-224(word.utf8).hex
 * result = hash[:16]
 * </pre>
 *
 * <p>注意：调用方需要把 {@code email + rawPassword} 拼接好后传入 {@link #encode(CharSequence)}；
 * 这是 rainbond 历史选择，不能改。
 */
@Component
public class LegacyPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("password material must not be null");
        }
        String input = rawPassword.toString();
        if (input.length() < 8) {
            throw new IllegalArgumentException("password material too short (need >= 8 chars, got " + input.length() + ")");
        }
        int c7 = input.charAt(7);
        int c5 = input.charAt(5);
        int c2 = input.charAt(2);
        String word = c7 + input + c5 + "goodrain" + (c2 / 7);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-224");
            byte[] hash = md.digest(word.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-224 not available", e);
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        return encode(rawPassword).equals(encodedPassword);
    }
}
