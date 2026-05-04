package cn.kuship.console.infrastructure.region.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Rainbond Go 集群响应的通用包装。
 *
 * <p>形状与 console 自身契约一致：
 * <pre>
 * { "code": 200, "msg": "success", "msg_show": "OK",
 *   "data": { "bean": {...}, "list": [...] } }
 * </pre>
 *
 * <p>Generic 参数 {@code T} 由调用方按 {@code data.bean} 实际类型指定。需要 list 时另调
 * {@code RegionApiResponseProcessor.extractList(...)}（或调用方在 T 处自定义带 list 字段的 wrapper）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegionApiResponse<T>(
        Integer code,
        String msg,
        @JsonProperty("msg_show") String msgShow,
        Data<T> data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data<T>(T bean, java.util.List<Object> list) {
    }
}
