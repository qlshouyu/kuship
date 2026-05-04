package cn.kuship.console.common.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
// jackson-annotations 包名仍是 com.fasterxml.jackson.annotation 在 Jackson 3 中（注解包未改）

import java.util.Map;

/**
 * 与 rainbond-console {@code general_message(code, msg, msg_show, bean=, list=, **kwargs)} 完全一致的响应包装。
 *
 * <p>序列化形状（顺序、命名严格保留）：
 * <pre>
 * { "code": 200, "msg": "success", "msg_show": "OK",
 *   "data": { "bean": {...}, "list": [...], ...kwargs } }
 * </pre>
 *
 * <p>字段命名约束：
 * <ul>
 *   <li>{@code msg_show} 使用 {@link JsonProperty} 显式锁定 snake_case，避免被任何全局 PropertyNamingStrategy 影响</li>
 *   <li>{@code data} 节点保持 {@code Map<String, Object>}，保留 Python 版「任意 kwargs 注入 data」的灵活性</li>
 * </ul>
 *
 * <p>构造请使用 {@link GeneralMessage} 的静态工厂方法。
 */
@JsonPropertyOrder({"code", "msg", "msg_show", "data"})
public record ApiResult(
        int code,
        String msg,
        @JsonProperty("msg_show") String msgShow,
        Map<String, Object> data
) {
}
