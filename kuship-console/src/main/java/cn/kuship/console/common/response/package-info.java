/**
 * 响应包装：与 rainbond-console 的 {@code general_message(code, msg, msg_show, bean=, list=, **kwargs)} 严格一致。
 *
 * <p>对外 JSON 形状：
 * <pre>
 * { "code": 200, "msg": "success", "msg_show": "OK",
 *   "data": { "bean": {...}, "list": [...], ...kwargs } }
 * </pre>
 *
 * <p>使用 {@link cn.kuship.console.common.response.GeneralMessage} 静态工厂构造。
 * 全局 {@code @ControllerAdvice} 由后续 change {@code migrate-console-response-contract} 接入。
 */
package cn.kuship.console.common.response;
