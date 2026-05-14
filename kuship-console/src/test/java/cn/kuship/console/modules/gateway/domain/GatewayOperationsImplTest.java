package cn.kuship.console.modules.gateway.domain;

import cn.kuship.console.infrastructure.region.RegionProperties;
import cn.kuship.console.infrastructure.region.api.GatewayOperationsImpl;
import cn.kuship.console.infrastructure.region.client.RegionClient;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.infrastructure.region.errormsg.RegionErrorMsgEnricher;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.response.RegionApiResponseProcessor;
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
 * GatewayOperationsImpl 单元测试（MockRestServiceServer）。
 */
@ExtendWith(MockitoExtension.class)
class GatewayOperationsImplTest {

    @Mock
    private RegionClientFactory clientFactory;

    private MockRestServiceServer mockServer;
    private GatewayOperationsImpl gatewayOps;
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

        cn.kuship.console.modules.account.repository.TenantsRepository mockTenantsRepo =
                org.mockito.Mockito.mock(cn.kuship.console.modules.account.repository.TenantsRepository.class);
        gatewayOps = new GatewayOperationsImpl(clientFactory, processor, JSON, mockTenantsRepo);
    }

    @Test
    void bindHttpDomain_success() {
        String responseBody = """
                {"code":200,"msg":"success","msg_show":"OK","data":{"bean":{"http_rule_id":"rule-001"},"list":[]}}
                """;
        mockServer.expect(requestTo("http://region-mock/v2/tenants/t1/http-rule"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        Map<String, Object> result = gatewayOps.bindHttpDomain("r1", "e1", "t1",
                Map.of("domain_name", "test.example.com"));

        assertThat(result).containsKey("http_rule_id");
        mockServer.verify();
    }

    @Test
    void bindHttpDomain_regionError_throws() {
        String errorBody = """
                {"code":500,"msg":"region error","msg_show":"集群错误","data":{"bean":{},"list":[]}}
                """;
        mockServer.expect(requestTo("http://region-mock/v2/tenants/t1/http-rule"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body(errorBody).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gatewayOps.bindHttpDomain("r1", "e1", "t1", Map.of()))
                .isInstanceOf(RegionApiException.class);
        mockServer.verify();
    }

    @Test
    void deleteHttpDomain_success() {
        String responseBody = """
                {"code":200,"msg":"success","msg_show":"OK","data":{"bean":{},"list":[]}}
                """;
        mockServer.expect(requestTo("http://region-mock/v2/tenants/t1/http-rule"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatCode(() -> gatewayOps.deleteHttpDomain("r1", "e1", "t1",
                Map.of("http_rule_id", "rule-001")))
                .doesNotThrowAnyException();
        mockServer.verify();
    }

    @Test
    void bindTcpDomain_success() {
        String responseBody = """
                {"code":200,"msg":"success","msg_show":"OK","data":{"bean":{"tcp_rule_id":"tcp-001"},"list":[]}}
                """;
        mockServer.expect(requestTo("http://region-mock/v2/tenants/t1/tcp-rule"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        Map<String, Object> result = gatewayOps.bindTcpDomain("r1", "e1", "t1",
                Map.of("end_point", "0.0.0.0:20001"));

        assertThat(result).containsKey("tcp_rule_id");
        mockServer.verify();
    }

    @Test
    void updateHttpDomain_success() {
        String responseBody = """
                {"code":200,"msg":"success","msg_show":"OK","data":{"bean":{"http_rule_id":"rule-001"},"list":[]}}
                """;
        mockServer.expect(requestTo("http://region-mock/v2/tenants/t1/http-rule"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        Map<String, Object> result = gatewayOps.updateHttpDomain("r1", "e1", "t1",
                Map.of("http_rule_id", "rule-001"));
        assertThat(result).isNotNull();
        mockServer.verify();
    }

    @Test
    void upgradeConfiguration_success() {
        String responseBody = """
                {"code":200,"msg":"success","msg_show":"OK","data":{"bean":{},"list":[]}}
                """;
        mockServer.expect(requestTo("http://region-mock/v2/tenants/t1/http-rule/rule-001/configurations"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        Map<String, Object> result = gatewayOps.upgradeConfiguration("r1", "e1", "t1", "rule-001",
                Map.of("connection_timeout", 60));
        assertThat(result).isNotNull();
        mockServer.verify();
    }

    @Test
    void unbindTcpDomain_regionError_throws() {
        String errorBody = """
                {"code":400,"msg":"tcp rule not found","msg_show":"TCP 规则不存在","data":{"bean":{},"list":[]}}
                """;
        mockServer.expect(requestTo("http://region-mock/v2/tenants/t1/tcp-rule"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withBadRequest().body(errorBody).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gatewayOps.unbindTcpDomain("r1", "e1", "t1",
                Map.of("tcp_rule_id", "tcp-001")))
                .isInstanceOf(RegionApiException.class);
        mockServer.verify();
    }
}
