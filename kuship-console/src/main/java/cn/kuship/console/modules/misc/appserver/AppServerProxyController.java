package cn.kuship.console.modules.misc.appserver;

import cn.kuship.console.common.response.SkipResponseWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Enumeration;
import java.util.Set;

/**
 * 复刻 rainbond {@code views/app_server_proxy.py::AppServerProxyView}：
 * 把 {@code /app-server/**} 全部 method 透传到 rainbond 公共应用市场。
 *
 * <p>默认目标 {@code https://hub.grapps.cn}（rainbond 6.7+ 起的新地址，已废弃 api.goodrain.com）。
 * 可通过配置 {@code kuship.app-server.proxy-target} 覆写（私有部署 / 离线包场景）。
 *
 * <p>鉴权：与 rainbond 端一致，公开匿名 —— SecurityConfig 已加白名单。
 *
 * <p>响应不走 general_message 自动包装（{@link SkipResponseWrapper}），原样透传 status / headers / body，
 * 避免破坏外部市场的 JSON / 文件下载契约。
 */
@RestController
public class AppServerProxyController {

    private static final Logger log = LoggerFactory.getLogger(AppServerProxyController.class);

    /** hop-by-hop headers per RFC 7230 §6.1 + content-length / encoding（避免响应体长度对不上）。 */
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade",
            "content-encoding", "content-length");

    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of(
            "host", "content-length", "connection");

    private final RestClient restClient;

    public AppServerProxyController(@Value("${kuship.app-server.proxy-target:https://hub.grapps.cn}")
                                     String proxyTarget) {
        this.restClient = RestClient.builder().baseUrl(proxyTarget).build();
        log.info("[app-server-proxy] target={}", proxyTarget);
    }

    @RequestMapping(value = "/app-server/**")
    @SkipResponseWrapper
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                         @RequestBody(required = false) byte[] body) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String fullPath = query != null && !query.isEmpty() ? path + "?" + query : path;

        try {
            RestClient.RequestBodySpec spec = restClient
                    .method(HttpMethod.valueOf(request.getMethod()))
                    .uri(fullPath)
                    .headers(h -> copyRequestHeaders(request, h));
            if (body != null && body.length > 0) {
                spec = (RestClient.RequestBodySpec) spec.body(body);
            }
            return spec.exchange((req, resp) -> {
                HttpHeaders out = filteredResponseHeaders(resp.getHeaders());
                byte[] respBody = resp.getBody().readAllBytes();
                return ResponseEntity.status(resp.getStatusCode()).headers(out).body(respBody);
            }, false);
        } catch (RestClientResponseException e) {
            // 上游 4xx/5xx —— 透传 status + body，不再包装
            HttpHeaders out = filteredResponseHeaders(e.getResponseHeaders() != null
                    ? e.getResponseHeaders() : new HttpHeaders());
            return ResponseEntity.status(e.getStatusCode()).headers(out)
                    .body(e.getResponseBodyAsByteArray());
        } catch (RuntimeException e) {
            log.warn("[app-server-proxy] {} {} failed: {}", request.getMethod(), fullPath, e.toString());
            return ResponseEntity.status(502)
                    .body(("upstream proxy error: " + e.getMessage()).getBytes());
        }
    }

    private static void copyRequestHeaders(HttpServletRequest request, HttpHeaders out) {
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            if (SKIP_REQUEST_HEADERS.contains(n.toLowerCase())) continue;
            Enumeration<String> values = request.getHeaders(n);
            while (values.hasMoreElements()) {
                out.add(n, values.nextElement());
            }
        }
        out.set("X-Real-IP", request.getRemoteAddr());
        out.set("X-Forwarded-Proto", request.getScheme());
        String xff = request.getHeader("X-Forwarded-For");
        out.set("X-Forwarded-For", xff != null ? xff : request.getRemoteAddr());
    }

    private static HttpHeaders filteredResponseHeaders(HttpHeaders src) {
        HttpHeaders out = new HttpHeaders();
        src.forEach((k, vs) -> {
            if (!HOP_BY_HOP.contains(k.toLowerCase())) {
                out.put(k, vs);
            }
        });
        return out;
    }
}
