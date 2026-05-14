package cn.kuship.console.modules.gateway.domain;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
import cn.kuship.console.modules.gateway.api.GatewayRouteOperationsImpl;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * GatewayRouteOperationsImpl 单元测试（MockRestServiceServer）。
 */
@ExtendWith(MockitoExtension.class)
class GatewayRouteOperationsImplTest {

    @Mock
    private RegionClientFactory clientFactory;

    private MockRestServiceServer mockServer;
    private GatewayRouteOperationsImpl routeOps;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static RegionProperties testProps() {
        return new RegionProperties(5, false, 0, List.of("操作过于频繁"));
    }

    @BeforeEach
    void setUp() {
        RegionErrorMsgEnricher enricher = new RegionErrorMsgEnricher();
        RegionApiResponseProcessor processor = new RegionApiResponseProcessor(JSON, testProps(), enricher);

        org.springframework.web.client.RestClient.Builder builder =
                RestClient.builder().baseUrl("http://region-mock");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        RegionClient regionClient = mock(RegionClient.class);
        when(regionClient.restClient()).thenReturn(restClient);
        when(clientFactory.getClient(anyString(), anyString())).thenReturn(regionClient);

        routeOps = new GatewayRouteOperationsImpl(clientFactory, processor);
    }

    @Test
    void listGatewayRoutes_success() {
        String responseBody = """
                {"code":200,"msg":"success","msg_show":"OK","data":{"bean":{"routes":[]},"list":[]}}
                """;
        mockServer.expect(requestTo("http://region-mock/v2/proxy-pass/gateway/t1/HTTPRoute"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        Map<String, Object> result = routeOps.listGatewayRoutes("r1", "e1", "t1", null);
        assertThat(result).isNotNull();
        mockServer.verify();
    }

    @Test
    void addGatewayRoute_success() {
        String responseBody = """
                {"code":200,"msg":"success","msg_show":"OK","data":{"bean":{"name":"route1"},"list":[]}}
                """;
        mockServer.expect(requestTo("http://region-mock/v2/proxy-pass/gateway/t1/HTTPRoute"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        Map<String, Object> result = routeOps.addGatewayRoute("r1", "e1", "t1", Map.of("name", "route1"));
        assertThat(result).containsKey("name");
        mockServer.verify();
    }

    @Test
    void deleteGatewayRoute_regionError_throws() {
        String errorBody = """
                {"code":404,"msg":"route not found","msg_show":"路由不存在","data":{"bean":{},"list":[]}}
                """;
        mockServer.expect(requestTo("http://region-mock/v2/proxy-pass/gateway/t1/HTTPRoute/route1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withResourceNotFound().body(errorBody).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> routeOps.deleteGatewayRoute("r1", "e1", "t1", "route1"))
                .isInstanceOf(RegionApiException.class);
        mockServer.verify();
    }
}
