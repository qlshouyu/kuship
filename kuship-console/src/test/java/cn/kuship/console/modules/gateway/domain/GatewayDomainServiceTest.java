package cn.kuship.console.modules.gateway.domain;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.GatewayOperations;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.application.entity.ServiceDomain;
import cn.kuship.console.modules.application.repository.ServiceDomainRepository;
import cn.kuship.console.modules.gateway.service.GatewayDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GatewayDomainService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class GatewayDomainServiceTest {

    @Mock
    private ServiceDomainRepository domainRepo;

    @Mock
    private GatewayOperations gatewayOps;

    private GatewayDomainService service;

    @BeforeEach
    void setUp() {
        service = new GatewayDomainService(domainRepo, gatewayOps);
    }

    @Test
    void bindHttpDomain_success() {
        when(domainRepo.findAll()).thenReturn(List.of()); // 无冲突
        ServiceDomain saved = new ServiceDomain();
        saved.setId(1);
        saved.setHttpRuleId("rule-abc");
        when(domainRepo.save(any())).thenReturn(saved);
        when(gatewayOps.bindHttpDomain(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(Map.of("http_rule_id", "rule-abc"));

        Map<String, Object> body = new HashMap<>();
        body.put("domain_name", "test.example.com");
        body.put("service_id", "svc-001");
        body.put("container_port", 80);
        body.put("tenant_id", "tenant-001");

        ServiceDomain result = service.bindHttpDomain("r1", "e1", "tn1", "tenant-001", body);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1);
        verify(gatewayOps).bindHttpDomain(eq("r1"), eq("e1"), eq("tn1"), anyMap());
    }

    @Test
    void bindHttpDomain_regionFails_rollback() {
        when(domainRepo.findAll()).thenReturn(List.of());
        ServiceDomain saved = new ServiceDomain();
        saved.setId(1);
        when(domainRepo.save(any())).thenReturn(saved);
        doThrow(new RegionApiException(500, "region error", "集群错误"))
                .when(gatewayOps).bindHttpDomain(anyString(), anyString(), anyString(), anyMap());

        assertThatThrownBy(() ->
                service.bindHttpDomain("r1", "e1", "tn1", "tenant-001",
                        Map.of("domain_name", "test.example.com", "service_id", "svc-001", "container_port", 80)))
                .isInstanceOf(RegionApiException.class);
        // 事务回滚由 Spring 保证，本测试验证 region 调用确实发生且异常被抛出
        verify(gatewayOps).bindHttpDomain(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void unbindHttpDomain_success() {
        ServiceDomain existing = new ServiceDomain();
        existing.setId(1);
        existing.setHttpRuleId("rule-001");
        when(domainRepo.findByHttpRuleId("rule-001")).thenReturn(Optional.of(existing));

        service.unbindHttpDomain("r1", "e1", "tn1", "rule-001");

        verify(gatewayOps).deleteHttpDomain(eq("r1"), eq("e1"), eq("tn1"), anyMap());
        verify(domainRepo).deleteById(1);
    }

    @Test
    void unbindHttpDomain_notFound_throws() {
        when(domainRepo.findByHttpRuleId("rule-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unbindHttpDomain("r1", "e1", "tn1", "rule-x"))
                .isInstanceOf(ServiceHandleException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void unbindHttpDomain_regionFails_localNotDeleted() {
        ServiceDomain existing = new ServiceDomain();
        existing.setId(1);
        existing.setHttpRuleId("rule-001");
        when(domainRepo.findByHttpRuleId("rule-001")).thenReturn(Optional.of(existing));
        doThrow(new RegionApiException(500, "error", "错误"))
                .when(gatewayOps).deleteHttpDomain(anyString(), anyString(), anyString(), anyMap());

        assertThatThrownBy(() -> service.unbindHttpDomain("r1", "e1", "tn1", "rule-001"))
                .isInstanceOf(RegionApiException.class);
        verify(domainRepo, never()).deleteById(any());
    }
}
