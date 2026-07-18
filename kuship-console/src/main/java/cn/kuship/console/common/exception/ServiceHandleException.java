package cn.kuship.console.common.exception;

/**
 * 业务异常，对齐 rainbond-console 的 {@code ServiceHandleException}：
 * 携带中文 {@code msgShow} 与 HTTP {@code status} / 业务 {@code code}，由全局异常处理透传。
 */
public class ServiceHandleException extends RuntimeException {

    /** HTTP 状态码 */
    private final int status;
    /** 业务信封 code（drf error_code）；默认回落为 {@link #status}，与既有行为一致 */
    private final int errorCode;
    /** 英文 msg */
    private final String msg;
    /** 中文展示文案 msg_show */
    private final String msgShow;

    /** 是否裸信封渲染（无 data），对齐 rainbond 早期校验错误。 */
    private final boolean bare;

    public ServiceHandleException(int status, String msg, String msgShow) {
        this(status, status, msg, msgShow);
    }

    /** 显式区分 HTTP status 与业务 errorCode（如无权：status=403、errorCode=10402）。 */
    public ServiceHandleException(int status, int errorCode, String msg, String msgShow) {
        this(status, errorCode, msg, msgShow, false);
    }

    private ServiceHandleException(int status, int errorCode, String msg, String msgShow, boolean bare) {
        super(msg);
        this.status = status;
        this.errorCode = errorCode;
        this.msg = msg;
        this.msgShow = msgShow;
        this.bare = bare;
    }

    public static ServiceHandleException badRequest(String msg, String msgShow) {
        return new ServiceHandleException(400, msg, msgShow);
    }

    /** region_name 缺失等早期校验：裸信封 {code:400, msg:"", msg_show:"请求参数不全"}，对齐 RegionTenantHeaderView。 */
    public static ServiceHandleException paramIncomplete() {
        return new ServiceHandleException(400, 400, "", "请求参数不全", true);
    }

    public boolean isBare() {
        return bare;
    }

    public static ServiceHandleException notFound(String msg, String msgShow) {
        return new ServiceHandleException(404, msg, msgShow);
    }

    public int getStatus() {
        return status;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getMsg() {
        return msg;
    }

    public String getMsgShow() {
        return msgShow;
    }
}
