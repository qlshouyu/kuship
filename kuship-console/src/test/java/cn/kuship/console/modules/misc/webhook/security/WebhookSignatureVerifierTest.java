package cn.kuship.console.modules.misc.webhook.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier();
    private static final String SECRET = "kuship-test-secret-1234567890ab";

    @Test
    void github_hmac_pass() throws Exception {
        byte[] body = "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + bytesToHex(hmac(SECRET, body));
        assertThat(verifier.verifyGitHub(body, header, SECRET)).isTrue();
    }

    @Test
    void github_hmac_fail_bad_signature() throws Exception {
        byte[] body = "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + bytesToHex(hmac("wrong-secret", body));
        assertThat(verifier.verifyGitHub(body, header, SECRET)).isFalse();
    }

    @Test
    void github_hmac_fail_missing_prefix() {
        // missing the "sha256=" prefix — closes fail
        assertThat(verifier.verifyGitHub("body".getBytes(), "abcdef", SECRET)).isFalse();
    }

    @Test
    void github_hmac_fail_malformed_hex() {
        // odd-length hex
        assertThat(verifier.verifyGitHub("body".getBytes(), "sha256=abc", SECRET)).isFalse();
    }

    @Test
    void gitlab_token_pass() {
        assertThat(verifier.verifyGitLab(SECRET, SECRET)).isTrue();
    }

    @Test
    void gitlab_token_fail() {
        assertThat(verifier.verifyGitLab("wrong-token", SECRET)).isFalse();
    }

    @Test
    void harbor_bearer_pass() {
        assertThat(verifier.verifyHarbor("Bearer " + SECRET, SECRET)).isTrue();
    }

    @Test
    void harbor_bearer_fail_wrong_token() {
        assertThat(verifier.verifyHarbor("Bearer wrong-token", SECRET)).isFalse();
    }

    @Test
    void harbor_bearer_fail_no_prefix() {
        assertThat(verifier.verifyHarbor(SECRET, SECRET)).isFalse();
    }

    @Test
    void custom_hmac_pass() throws Exception {
        byte[] body = "{\"event\":\"deploy\"}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + bytesToHex(hmac(SECRET, body));
        assertThat(verifier.verifyCustom(body, header, SECRET)).isTrue();
    }

    @Test
    void custom_hmac_fail() throws Exception {
        byte[] body = "{\"event\":\"deploy\"}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + bytesToHex(hmac("wrong-secret", body));
        assertThat(verifier.verifyCustom(body, header, SECRET)).isFalse();
    }

    @Test
    void hex_to_bytes_round_trip() {
        byte[] expected = {0x00, (byte) 0xff, 0x10, 0x4a};
        assertThat(WebhookSignatureVerifier.hexToBytes("00ff104a")).containsExactly(expected);
        assertThat(WebhookSignatureVerifier.hexToBytes("00FF104A")).containsExactly(expected);
        assertThat(WebhookSignatureVerifier.hexToBytes(null)).isNull();
        assertThat(WebhookSignatureVerifier.hexToBytes("abc")).isNull();         // odd length
        assertThat(WebhookSignatureVerifier.hexToBytes("zz")).isNull();          // non-hex
    }

    private static byte[] hmac(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(body);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
