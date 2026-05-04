package cn.kuship.console.modules.misc.sms.provider;

/**
 * Abstraction over SMS gateways (add-aliyun-sms).
 *
 * <p>Implementations are selected at startup via {@code kuship.sms.provider}:
 * <ul>
 *   <li>{@code logging} (default) — {@code LoggingSmsProvider}, dev/local-safe, prints the code.</li>
 *   <li>{@code aliyun} — {@code AliyunSmsProvider}, calls aliyun-dysmsapi 2.0.</li>
 * </ul>
 *
 * <p>Adding a new gateway means adding a {@code @Component} that implements this interface and
 * gating it with {@code @ConditionalOnProperty(name = "kuship.sms.provider", havingValue = "<id>")}.
 */
public interface SmsProvider {

    /**
     * Send a verification code to {@code phone}.
     *
     * <p>Implementations should NOT throw on remote failure — they should return {@link SmsResult}
     * with {@link SmsResult#success()} false and an {@code error} message, so the caller can
     * decide whether to roll the surrounding DB transaction back. Throwing should be reserved for
     * programmer errors (null inputs, mis-configured client).
     *
     * @param phone   11-digit Chinese phone number (already validated upstream)
     * @param code    6-digit verification code (already generated upstream)
     * @param purpose business purpose, e.g. {@code login} or {@code register} — the provider may
     *                use this to pick a template variant (the default Aliyun provider ignores it
     *                and uses the global {@code template-code}).
     */
    SmsResult send(String phone, String code, String purpose);

    /** Identifier returned in {@link #identifier()} so logs can attribute which gateway sent. */
    String identifier();

    /**
     * Result envelope. Success path carries the gateway-side message id (e.g. aliyun BizId);
     * failure path carries an error code + human message that callers can surface to operators.
     */
    record SmsResult(boolean success, String messageId, String errorCode, String errorMessage) {
        public static SmsResult ok(String messageId) {
            return new SmsResult(true, messageId, null, null);
        }
        public static SmsResult fail(String errorCode, String errorMessage) {
            return new SmsResult(false, null, errorCode, errorMessage);
        }
    }
}
