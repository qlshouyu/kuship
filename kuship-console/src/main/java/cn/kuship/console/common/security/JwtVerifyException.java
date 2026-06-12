package cn.kuship.console.common.security;

/** JWT 验签/解析失败（签名不符、格式错误、已过期）。 */
public class JwtVerifyException extends RuntimeException {

    public JwtVerifyException(String message) {
        super(message);
    }
}
