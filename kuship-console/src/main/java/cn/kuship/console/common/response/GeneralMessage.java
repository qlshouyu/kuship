package cn.kuship.console.common.response;

import java.util.List;

/**
 * 构造 {@link ApiResult} 信封的工厂，镜像 rainbond-console 的 {@code general_message} / {@code error_message}。
 */
public final class GeneralMessage {

    private GeneralMessage() {
    }

    /** general_message(code, msg, msg_show, bean=...) */
    public static ApiResult bean(int code, String msg, String msgShow, Object bean) {
        return ApiResult.of(code, msg, msgShow, bean, null);
    }

    /** general_message(code, msg, msg_show, list=...) */
    public static ApiResult list(int code, String msg, String msgShow, List<?> list) {
        return ApiResult.of(code, msg, msgShow, null, list);
    }

    /** general_message(code, msg, msg_show, list=..., total=...) —— total 放在 data 顶层 */
    public static ApiResult page(int code, String msg, String msgShow, List<?> list, long total) {
        return ApiResult.of(code, msg, msgShow, null, list).putExtra("total", total);
    }

    /** general_message(code, msg, msg_show) —— 仅消息，无数据 */
    public static ApiResult message(int code, String msg, String msgShow) {
        return ApiResult.of(code, msg, msgShow, null, null);
    }

    /** error_message() —— 统一系统异常 */
    public static ApiResult error(String enMsg) {
        return message(500, enMsg != null ? enMsg : "system error", "系统异常");
    }
}
