package cn.kuship.console.common.exception;

import lombok.Getter;

/**
 * 业务异常基类，对齐 rainbond-console 的 {@code ServiceHandleException}。
 *
 * <p>抛出后会被全局 {@code @ControllerAdvice} 转成
 * {@code {"code": <code>, "msg": <msg>, "msg_show": <msgShow>, "data": {bean:{}, list:[]}}}。
 *
 * <p>本 change 不挂 ControllerAdvice，仅作为类型占位，避免后续业务 change 引用时找不到类。
 */
@Getter
public class ServiceHandleException extends RuntimeException {

    private final int code;
    private final String msgShow;

    public ServiceHandleException(int code, String msg, String msgShow) {
        super(msg);
        this.code = code;
        this.msgShow = msgShow;
    }

    public ServiceHandleException(int code, String msg, String msgShow, Throwable cause) {
        super(msg, cause);
        this.code = code;
        this.msgShow = msgShow;
    }

    public String getMsg() {
        return getMessage();
    }
}
