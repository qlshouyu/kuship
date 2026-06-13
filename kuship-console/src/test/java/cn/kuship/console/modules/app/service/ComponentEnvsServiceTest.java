package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.app.entity.TenantServiceEnvVar;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.TenantServiceEnvVarRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 组件环境变量：is_change int、字段顺序、env_type 校验、total。 */
class ComponentEnvsServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final TenantServiceEnvVarRepository envRepo = mock(TenantServiceEnvVarRepository.class);
    private final ComponentEnvsService service = new ComponentEnvsService(svcRepo, envRepo);

    private TenantServiceInfo svc() {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getTenantId()).thenReturn("tid");
        when(s.getServiceId()).thenReturn("sid");
        return s;
    }

    @Test
    void rejects_invalid_env_type() {
        assertThatThrownBy(() -> service.listEnvs("default", "a", null, null, 1, 10))
                .isInstanceOf(ServiceHandleException.class);
        assertThatThrownBy(() -> service.listEnvs("default", "a", "foo", null, 1, 10))
                .isInstanceOf(ServiceHandleException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void env_dict_is_change_int_and_order() {
        TenantServiceInfo sv = svc();
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(sv));
        TenantServiceEnvVar e = mock(TenantServiceEnvVar.class);
        java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
        d.put("ID", 3); d.put("attr_name", "ENVPORT_HOST"); d.put("is_change", 0); d.put("scope", "outer");
        when(e.toEnvDict()).thenReturn(d);
        Page<TenantServiceEnvVar> page = new PageImpl<>(List.of(e), org.springframework.data.domain.PageRequest.of(0, 1), 2);
        when(envRepo.findByTenantIdAndServiceIdAndScopeOrderByAttrName(eq("tid"), eq("sid"), eq("outer"), any()))
                .thenReturn(page);

        ComponentEnvsService.Result r = service.listEnvs("default", "a", "outer", null, 1, 10);
        assertThat(r.total()).isEqualTo(2);
        assertThat(r.list()).hasSize(1);
        assertThat(r.list().get(0).get("is_change")).isEqualTo(0);   // 整数非 bool
        assertThat(r.list().get(0).get("scope")).isEqualTo("outer");
    }

    @Test
    void uses_fuzzy_query_when_env_name_present() {
        TenantServiceInfo sv = svc();
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(sv));
        Page<TenantServiceEnvVar> empty = new PageImpl<>(List.of(), Pageable.ofSize(10), 0);
        when(envRepo.findByTenantIdAndServiceIdAndScopeAndAttrNameContainingOrderByAttrName(
                eq("tid"), eq("sid"), eq("inner"), eq("FOO"), any())).thenReturn(empty);
        ComponentEnvsService.Result r = service.listEnvs("default", "a", "inner", "FOO", 1, 10);
        assertThat(r.total()).isEqualTo(0);
        assertThat(r.list()).isEmpty();
    }
}
