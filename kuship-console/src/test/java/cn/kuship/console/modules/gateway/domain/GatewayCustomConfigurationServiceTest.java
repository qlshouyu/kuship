package cn.kuship.console.modules.gateway.domain;

import cn.kuship.console.infrastructure.region.api.GatewayOperations;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.gateway.entity.GatewayCustomConfigure;
import cn.kuship.console.modules.gateway.repository.GatewayCustomConfigureRepository;
import cn.kuship.console.modules.gateway.service.GatewayCustomConfigurationService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GatewayCustomConfigurationService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class GatewayCustomConfigurationServiceTest {

    @Mock
    private GatewayCustomConfigureRepository configRepo;

    @Mock
    private GatewayOperations gatewayOps;

    private GatewayCustomConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new GatewayCustomConfigurationService(configRepo, gatewayOps, new ObjectMapper());
    }

    @Test
    void getValue_notFound_returnsEmpty() {
        when(configRepo.findByRuleId("rule-001")).thenReturn(Optional.empty());
        assertThat(service.getValue("rule-001")).isEmpty();
    }

    @Test
    void getValue_withData_returnsMap() {
        GatewayCustomConfigure cfg = new GatewayCustomConfigure();
        cfg.setRuleId("rule-001");
        cfg.setValue("{\"connection_timeout\":60,\"set_headers\":{}}");
        when(configRepo.findByRuleId("rule-001")).thenReturn(Optional.of(cfg));

        Map<String, Object> result = service.getValue("rule-001");
        assertThat(result).containsKey("connection_timeout");
        assertThat(result.get("connection_timeout")).isEqualTo(60);
    }

    @Test
    void setValue_success_savesToRepo() {
        when(configRepo.findByRuleId("rule-001")).thenReturn(Optional.empty());
        when(configRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> config = Map.of("connection_timeout", 30, "set_headers", Map.of());
        service.setValue("region1", "eid1", "tenant1", "rule-001", config);

        verify(gatewayOps).upgradeConfiguration(eq("region1"), eq("eid1"), eq("tenant1"),
                eq("rule-001"), eq(config));
        verify(configRepo).save(argThat(c -> "rule-001".equals(c.getRuleId())));
    }

    @Test
    void setValue_regionFails_localNotSaved() {
        doThrow(new RegionApiException(500, "region error", "集群错误"))
                .when(gatewayOps).upgradeConfiguration(anyString(), anyString(), anyString(),
                        anyString(), anyMap());

        assertThatThrownBy(() ->
                service.setValue("region1", "eid1", "tenant1", "rule-001", Map.of("key", "val")))
                .isInstanceOf(RegionApiException.class);

        verify(configRepo, never()).save(any());
    }
}
