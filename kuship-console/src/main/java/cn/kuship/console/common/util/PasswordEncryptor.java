package cn.kuship.console.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 复刻 rainbond-console {@code www/utils/crypt.py} 的 {@code encrypt_passwd}，用于与既有 user_info
 * 密码哈希互认。<b>必须与 Python 实现逐字节一致</b>，否则无法登录既有账号。
 *
 * <pre>
 * def encrypt_passwd(string):
 *     new_word = str(ord(string[7])) + string + str(ord(string[5])) + 'goodrain' + str(int(ord(string[2]) / 7))
 *     return hashlib.sha224(new_word.encode("utf-8")).hexdigest()[0:16]
 * </pre>
 *
 * 其中 {@code string = email + raw_password}（见 {@code Users.set_password / check_password}）。
 */
public final class PasswordEncryptor {

    private PasswordEncryptor() {
    }

    /** 对齐 Users.check_password：encrypt_passwd(email + rawPassword) 与库中 password 比对。 */
    public static boolean matches(String email, String rawPassword, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                encrypt(email + rawPassword).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    /** encrypt_passwd(string) */
    public static String encrypt(String string) {
        // Python 使用 unicode 码点；ASCII 邮箱/密码下与 char 值一致
        int c7 = string.charAt(7);
        int c5 = string.charAt(5);
        int c2 = string.charAt(2);
        // int(ord(string[2]) / 7)：正数向零截断 == 整数除法
        String newWord = Integer.toString(c7) + string + Integer.toString(c5) + "goodrain" + Integer.toString(c2 / 7);
        byte[] digest = sha224(newWord.getBytes(StandardCharsets.UTF_8));
        String hex = toHex(digest);
        return hex.substring(0, 16);
    }

    private static byte[] sha224(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-224").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-224 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
