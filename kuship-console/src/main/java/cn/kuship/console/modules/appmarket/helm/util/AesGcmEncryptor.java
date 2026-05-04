package cn.kuship.console.modules.appmarket.helm.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM 加密器，用于持久化 Helm Repo 密码等敏感字段。
 *
 * <p>密钥来自 `kuship.helm.repo-password-key` 配置；缺失时 dev/local profile 退化到明文（带告警），
 * 其他 profile 启动失败拒绝运行。
 */
@Component
public class AesGcmEncryptor {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final byte[] keyBytes;
    private final boolean fallbackPlain;
    private final SecureRandom rng = new SecureRandom();

    public AesGcmEncryptor(@Value("${kuship.helm.repo-password-key:}") String key,
                              @Value("${spring.profiles.active:default}") String activeProfile) {
        if (key == null || key.isBlank()) {
            boolean isDev = activeProfile.contains("local") || activeProfile.contains("dev")
                    || activeProfile.contains("contract-test") || activeProfile.equals("default");
            if (!isDev) {
                throw new IllegalStateException(
                        "kuship.helm.repo-password-key required for profile: " + activeProfile);
            }
            this.keyBytes = null;
            this.fallbackPlain = true;
        } else {
            byte[] raw = key.getBytes(StandardCharsets.UTF_8);
            byte[] derived = new byte[32];
            for (int i = 0; i < derived.length; i++) {
                derived[i] = raw[i % raw.length];
            }
            this.keyBytes = derived;
            this.fallbackPlain = false;
        }
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        if (fallbackPlain) return plain;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            rng.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return "AES:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES encrypt failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith("AES:")) return stored;
        if (fallbackPlain) return stored;
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(4));
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES decrypt failed", e);
        }
    }
}
