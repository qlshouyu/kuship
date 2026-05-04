package cn.kuship.console.modules.misc.webhook.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * Validates webhook signatures from GitHub, GitLab, Harbor and kuship custom callers
 * (harden-webhook-hmac).
 *
 * <p>All comparisons run in constant time via {@link MessageDigest#isEqual(byte[], byte[])}
 * to avoid timing side channels. The HMAC-SHA256 path follows GitHub's canonical
 * {@code sha256=<hex>} envelope used by both GitHub and the kuship custom flow.
 *
 * <p>The four protocols differ as follows:
 * <ul>
 *   <li><b>GitHub</b> — header {@code X-Hub-Signature-256}, body-bound HMAC-SHA256</li>
 *   <li><b>GitLab</b> — header {@code X-Gitlab-Token}, plain token (no body signature)</li>
 *   <li><b>Harbor</b> — header {@code Authorization: Bearer &lt;token&gt;}, plain token</li>
 *   <li><b>custom</b> — header {@code X-Kuship-Signature}, body-bound HMAC-SHA256</li>
 * </ul>
 *
 * <p>Returning {@code false} for any malformed input is intentional: the trigger controller
 * treats {@code false} as authentication failure and rejects the request without leaking which
 * branch (header parse, hex decode, HMAC mismatch) caused the rejection.
 */
@Component
public class WebhookSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String GITHUB_PREFIX = "sha256=";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * GitHub webhook: HMAC-SHA256 over raw request body.
     */
    public boolean verifyGitHub(byte[] body, String headerSig, String secret) {
        return verifyHmacSha256(body, headerSig, secret);
    }

    /**
     * Custom kuship webhook: same envelope as GitHub but a different header name —
     * the trigger controller passes the {@code X-Kuship-Signature} header through here.
     */
    public boolean verifyCustom(byte[] body, String headerSig, String secret) {
        return verifyHmacSha256(body, headerSig, secret);
    }

    /**
     * GitLab webhook: plain token comparison, no body signature.
     */
    public boolean verifyGitLab(String headerToken, String secret) {
        if (headerToken == null || secret == null || secret.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                headerToken.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Harbor webhook: parses {@code Authorization: Bearer <token>} and compares the token.
     */
    public boolean verifyHarbor(String authHeader, String secret) {
        if (authHeader == null || secret == null || secret.isEmpty()) {
            return false;
        }
        if (!authHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
    }

    private boolean verifyHmacSha256(byte[] body, String headerSig, String secret) {
        if (body == null || headerSig == null || secret == null || secret.isEmpty()) {
            return false;
        }
        if (!headerSig.startsWith(GITHUB_PREFIX)) {
            return false;
        }
        byte[] expected = hexToBytes(headerSig.substring(GITHUB_PREFIX.length()));
        if (expected == null) {
            return false;
        }
        byte[] actual;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            actual = mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            return false;
        }
        return MessageDigest.isEqual(actual, expected);
    }

    /**
     * Lower-case hex decoder. Returns null on any malformed input (odd length, non-hex char)
     * so callers fail closed rather than throwing inside the verifier.
     */
    static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return null;
        }
        int len = hex.length();
        if ((len & 1) != 0) {
            return null;
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = hexDigit(hex.charAt(i));
            int lo = hexDigit(hex.charAt(i + 1));
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }
}
