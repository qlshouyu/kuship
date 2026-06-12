package cn.kuship.console.common.exception;

/**
 * 业务异常，对齐 rainbond-console 的 {@code ServiceHandleException}：
 * 携带中文 {@code msgShow} 与 HTTP {@code status} / 业务 {@code code}，由全局异常处理透传。
 */
public class ServiceHandleException extends RuntimeException {

    /** HTTP 状态码（同时作为信封 code） */
    private final int status;
    /** 英文 msg */
    private final String msg;
    /** 中文展示文案 msg_show */
    private final String msgShow;

    public ServiceHandleException(int status, String msg, String msgShow) {
        super(msg);
        this.status = status;
        this.msg = msg;
        this.msgShow = msgShow;
    }

    public static ServiceHandleException badRequest(String msg, String msgShow) {
        return new ServiceHandleException(400, msg, msgShow);
    }

    public static ServiceHandleException notFound(String msg, String msgShow) {
        return new ServiceHandleException(404, msg, msgShow);
    }

    public int getStatus() {
        return status;
    }

    public String getMsg() {
        return msg;
    }

    public String getMsgShow() {
        return msgShow;
    }
}
