package cn.kuship.console.common.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 静态工厂：构造与 rainbond-console {@code general_message} 等价的 {@link ApiResult}。
 *
 * <h2>语义对齐 Python 版</h2>
 * <pre>
 * # rainbond-console: www/utils/return_message.py
 * def general_message(code, msg, msg_show, bean=None, list=None, *args, **kwargs):
 *     return {"code": code, "msg": msg, "msg_show": msg_show,
 *             "data": dict(bean=bean or {}, list=list or [], **kwargs)}
 * </pre>
 *
 * <p>{@code data} 节点必定包含 {@code bean} 与 {@code list} 两个键（缺省给空对象/空数组），
 * 同时支持任意额外 kwargs（通过 {@link #ok(String, Map)} 与 {@link #okWithExtras(Map, List, Map)} 注入）。
 *
 * <p>本 change 不挂全局 {@code @ControllerAdvice}，controller 需显式调用本类构造响应。
 * 全局包装/异常映射由后续 change {@code migrate-console-response-contract} 落地。
 */
public final class GeneralMessage {

    public static final int CODE_OK = 200;
    public static final int CODE_INTERNAL_ERROR = 500;
    public static final String MSG_SUCCESS = "success";
    public static final String MSG_SHOW_OK = "OK";

    private GeneralMessage() {
    }

    public static ApiResult ok() {
        return ok(MSG_SHOW_OK, null, null, null);
    }

    public static ApiResult ok(Map<String, Object> bean) {
        return ok(MSG_SHOW_OK, bean, null, null);
    }

    public static ApiResult okList(List<?> list) {
        return ok(MSG_SHOW_OK, null, list, null);
    }

    public static ApiResult ok(String msgShow, Map<String, Object> extras) {
        return ok(msgShow, null, null, extras);
    }

    public static ApiResult okWithExtras(Map<String, Object> bean, List<?> list, Map<String, Object> extras) {
        return ok(MSG_SHOW_OK, bean, list, extras);
    }

    public static ApiResult error(int code, String msg, String msgShow) {
        return new ApiResult(code, msg, msgShow, buildData(null, null, null));
    }

    private static ApiResult ok(String msgShow, Map<String, Object> bean, List<?> list, Map<String, Object> extras) {
        return new ApiResult(CODE_OK, MSG_SUCCESS, msgShow, buildData(bean, list, extras));
    }

    /**
     * 构造 {@code data} 节点：保证至少含 {@code bean} 与 {@code list}；保留插入顺序以利于人眼调试。
     */
    private static Map<String, Object> buildData(Map<String, Object> bean, List<?> list, Map<String, Object> extras) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bean", bean != null ? bean : Collections.emptyMap());
        data.put("list", list != null ? list : Collections.emptyList());
        if (extras != null) {
            for (Map.Entry<String, Object> e : extras.entrySet()) {
                if ("bean".equals(e.getKey()) || "list".equals(e.getKey())) {
                    continue;
                }
                data.put(e.getKey(), e.getValue());
            }
        }
        return data;
    }
}
