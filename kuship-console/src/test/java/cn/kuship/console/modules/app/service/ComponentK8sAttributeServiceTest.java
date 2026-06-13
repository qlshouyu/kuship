package cn.kuship.console.modules.app.service;

import cn.kuship.console.modules.app.entity.ComponentK8sAttributes;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.ComponentK8sAttributesRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** k8s 属性：json 对象→[{key,value}]、数组透传、非 json 原样、空。 */
class ComponentK8sAttributeServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final ComponentK8sAttributesRepository attrRepo = mock(ComponentK8sAttributesRepository.class);
    private final ComponentK8sAttributeService service = new ComponentK8sAttributeService(svcRepo, attrRepo);

    private void wire(ComponentK8sAttributes... attrs) {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceId()).thenReturn("sid");
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        when(attrRepo.findByComponentId("sid")).thenReturn(List.of(attrs));
    }

    private ComponentK8sAttributes attr(String saveType, String value) {
        ComponentK8sAttributes a = mock(ComponentK8sAttributes.class);
        when(a.getSaveType()).thenReturn(saveType);
        when(a.getAttributeValue()).thenReturn(value);
        when(a.toDict(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("save_type", saveType);
            m.put("attribute_value", inv.getArgument(0) != null ? inv.getArgument(0) : value);
            return m;
        });
        return a;
    }

    @Test
    @SuppressWarnings("unchecked")
    void json_object_to_kv_list() {
        wire(attr("json", "{\"K\":\"V\"}"));
        Object v = service.listAttributes("a").get(0).get("attribute_value");
        assertThat((List<Map<String, Object>>) v).containsExactly(Map.of("key", "K", "value", "V"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void json_array_passthrough() {
        wire(attr("json", "[\"a\",\"b\"]"));
        Object v = service.listAttributes("a").get(0).get("attribute_value");
        assertThat((List<Object>) v).containsExactly("a", "b");
    }

    @Test
    void non_json_raw() {
        wire(attr("string", "/data"));
        assertThat(service.listAttributes("a").get(0).get("attribute_value")).isEqualTo("/data");
    }

    @Test
    void empty() {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceId()).thenReturn("sid");
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        when(attrRepo.findByComponentId("sid")).thenReturn(List.of());
        assertThat(service.listAttributes("a")).isEmpty();
    }
}
