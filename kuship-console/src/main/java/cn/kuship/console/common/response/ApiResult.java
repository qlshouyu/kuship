package cn.kuship.console.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一响应信封，1:1 对齐 rainbond-console 的 {@code general_message}：
 * <pre>{"code", "msg", "msg_show", "data": {"bean": {}, "list": [], ...extras}}</pre>
 *
 * <p>顶层字段顺序固定 code → msg → msg_show → data；{@code data} 恒含 bean(默认 {}) 与 list(默认 [])。
 * 额外的键（如分页 total）直接放在 data 顶层，与 {@code general_message(..., total=total)} 一致。
 */
@JsonPropertyOrder({"code", "msg", "msg_show", "data"})
public class ApiResult {

    private int code;
    private String msg;

    @JsonProperty("msg_show")
    private String msgShow;

    /** data 为 null 时整体省略（对齐 rainbond 裸校验错误如「请求参数不全」，仅 code/msg/msg_show）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> data;

    public ApiResult() {
    }

    public ApiResult(int code, String msg, String msgShow, Map<String, Object> data) {
        this.code = code;
        this.msg = msg;
        this.msgShow = msgShow;
        this.data = data;
    }

    /** 构造一个保证含 bean/list 的 data 容器（bean、list 始终在前）。 */
    private static Map<String, Object> newData(Object bean, List<?> list) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bean", bean != null ? bean : new LinkedHashMap<>());
        data.put("list", list != null ? list : List.of());
        return data;
    }

    public static ApiResult of(int code, String msg, String msgShow, Object bean, List<?> list) {
        return new ApiResult(code, msg, msgShow, newData(bean, list));
    }

    public ApiResult putExtra(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getMsgShow() {
        return msgShow;
    }

    public void setMsgShow(String msgShow) {
        this.msgShow = msgShow;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
