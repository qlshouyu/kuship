package cn.kuship.console.infrastructure.region.client;

import cn.kuship.console.infrastructure.region.repository.RegionInfoDto;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.web.client.RestClient;

import java.io.Closeable;
import java.io.IOException;

/**
 * 单个 region 的 HTTP 客户端封装，由 {@link RegionClientFactory} 创建。
 *
 * <p>对调用方暴露：
 * <ul>
 *   <li>{@link #restClient()} —— 用于发起请求</li>
 *   <li>{@link #regionInfo()} —— region 元数据（url/wsurl/token 等）</li>
 * </ul>
 */
public final class RegionClient implements Closeable {

    private final RegionInfoDto regionInfo;
    private final RestClient restClient;
    private final CloseableHttpClient httpClient;

    public RegionClient(RegionInfoDto regionInfo, RestClient restClient, CloseableHttpClient httpClient) {
        this.regionInfo = regionInfo;
        this.restClient = restClient;
        this.httpClient = httpClient;
    }

    public RegionInfoDto regionInfo() {
        return regionInfo;
    }

    public RestClient restClient() {
        return restClient;
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
