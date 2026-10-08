package cn.kuship.console.common.exception;

/**
 * region API 返回 ≥400 时抛出（对齐 rainbond RegionApiBaseHttpClient.CallApiError）。
 * 携带 rainbond CallApiError.message 同款结构（apitype/url/method/httpcode/body），
 * 由 GlobalExceptionHandler 渲染为裸信封：
 * <ul>
 *   <li>region 404 → HTTP 404 {@code {code:404,msg:"region no found this resource",msg_show:"数据中心资源不存在"}}</li>
 *   <li>其余 → HTTP 400 {@code {code:400,msg:{apitype,url,method,httpcode,body},msg_show:"数据中心操作故障 <core>"}}</li>
 * </ul>
 * 继承 ServiceHandleException 以保持既有服务层 catch/透传行为不变。
 */
public class RegionCallException extends ServiceHandleException {

    private final String url;
    private final String method;
    private final int httpcode;
    private final Object body;

    public RegionCallException(String url, String method, int httpcode, Object body) {
        super(httpcode == 404 ? 404 : 400, "region error: " + httpcode, "集群请求失败");
        this.url = url;
        this.method = method;
        this.httpcode = httpcode;
        this.body = body;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public int getHttpcode() {
        return httpcode;
    }

    public Object getBody() {
        return body;
    }
}
