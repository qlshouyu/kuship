package cn.kuship.console.infrastructure.region.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Region API 调用错误的根异常。
 *
 * <p>HTTP 状态码与业务 {@code code} 解耦（与 console 自身契约一致）：{@link #httpStatus} 仅用于 debug，
 * 对外响应体的 {@code code} 字段使用 {@link #code}（来自 region 响应 body 的 {@code code} 字段）。
 *
 * <p>{@link #msgShow} 已经过 {@code RegionErrorMsgEnricher} 汉化（如适用）。
 */
@Getter
public class RegionApiException extends RuntimeException {

    private final String apiType;
    private final String url;
    private final String method;
    private final int httpStatus;
    /** 业务码：来自 region 响应 body 的 code 字段，与 HTTP 状态码独立。 */
    private final int code;
    /** 原始英文/技术消息，便于排错。 */
    private final String msg;
    /** 给最终用户看的中文消息。 */
    private final String msgShow;
    /** region 响应 body 中 data.bean 的内容；可为空。 */
    private final Map<String, Object> bean;

    public RegionApiException(int code, String msg, String msgShow) {
        this(null, null, null, 0, code, msg, msgShow, Map.of(), null);
    }

    public RegionApiException(String apiType, String url, String method, int httpStatus,
                              int code, String msg, String msgShow,
                              Map<String, Object> bean, Throwable cause) {
        super(msg, cause);
        this.apiType = apiType;
        this.url = url;
        this.method = method;
        this.httpStatus = httpStatus;
        this.code = code;
        this.msg = msg;
        this.msgShow = msgShow;
        this.bean = bean == null ? Map.of() : bean;
    }
}
