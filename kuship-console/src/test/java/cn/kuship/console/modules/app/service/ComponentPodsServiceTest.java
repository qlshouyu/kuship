package cn.kuship.console.modules.app.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.team.entity.TenantRegionInfo;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.TenantRegionInfoRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 组件实例：转换、跳过 POD、内存换算、主容器置首、空 bean。 */
class ComponentPodsServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final TenantsRepository tenantsRepo = mock(TenantsRepository.class);
    private final TenantRegionInfoRepository trRepo = mock(TenantRegionInfoRepository.class);
    private final RegionClient client = mock(RegionClient.class);
    private final ComponentPodsService service = new ComponentPodsService(svcRepo, tenantsRepo, trRepo, client);

    private void wire(String k8sName) {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceRegion()).thenReturn("rainbond");
        when(s.getK8sComponentName()).thenReturn(k8sName);
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        Tenants t = mock(Tenants.class);
        when(t.getTenantId()).thenReturn("tid");
        when(t.getTenantName()).thenReturn("default");
        when(t.getEnterpriseId()).thenReturn("eid");
        when(tenantsRepo.findByTenantName("default")).thenReturn(Optional.of(t));
        TenantRegionInfo tr = mock(TenantRegionInfo.class);
        when(tr.getRegionTenantName()).thenReturn("default");
        when(trRepo.findByTenantIdAndRegionName("tid", "rainbond")).thenReturn(Optional.of(tr));
    }

    @Test
    @SuppressWarnings("unchecked")
    void converts_and_skips_pod_and_computes_memory() {
        wire("parity");
        // 536870912 bytes = 512MB；2269184 = 2.16MB；含 POD 容器应跳过
        when(client.exchange(any(), any(), any(), any(), any(), any())).thenReturn(
                "{\"bean\":{\"new_pods\":[{\"pod_name\":\"p1\",\"pod_status\":\"RUNNING\",\"container\":{"
                + "\"POD\":{\"memory_limit\":\"0\",\"memory_usage\":\"0\"},"
                + "\"parity\":{\"memory_limit\":\"536870912\",\"memory_usage\":\"2269184\"}}}],\"old_pods\":null}}");

        Map<String, Object> r = service.listPods("default", "a", "rainbond");
        List<Map<String, Object>> newPods = (List<Map<String, Object>>) r.get("new_pods");
        assertThat(r.get("old_pods")).isNull();
        assertThat(newPods).hasSize(1);
        Map<String, Object> pod = newPods.get(0);
        assertThat(pod.get("pod_name")).isEqualTo("p1");
        assertThat(pod.get("pod_status")).isEqualTo("RUNNING");
        assertThat(pod.get("manage_name")).isEqualTo("manager");
        List<Map<String, Object>> containers = (List<Map<String, Object>>) pod.get("container");
        assertThat(containers).hasSize(1); // POD skipped
        Map<String, Object> c = containers.get(0);
        assertThat(c.get("container_name")).isEqualTo("parity");
        assertThat((double) c.get("memory_limit")).isEqualTo(512.0);
        assertThat((double) c.get("memory_usage")).isEqualTo(2.16);
        assertThat((double) c.get("usage_rate")).isEqualTo(0.42);
        // pod bean 键顺序
        assertThat(pod.keySet()).containsExactly("pod_name", "pod_status", "manage_name", "container");
    }

    @Test
    @SuppressWarnings("unchecked")
    void main_container_moved_to_front() {
        wire("main");
        when(client.exchange(any(), any(), any(), any(), any(), any())).thenReturn(
                "{\"bean\":{\"new_pods\":[{\"pod_name\":\"p\",\"pod_status\":\"RUNNING\",\"container\":{"
                + "\"sidecar\":{\"memory_limit\":\"1048576\",\"memory_usage\":\"0\"},"
                + "\"main-app\":{\"memory_limit\":\"1048576\",\"memory_usage\":\"0\"}}}],\"old_pods\":null}}");
        Map<String, Object> r = service.listPods("default", "a", "rainbond");
        List<Map<String, Object>> containers =
                (List<Map<String, Object>>) ((List<Map<String, Object>>) r.get("new_pods")).get(0).get("container");
        // main-app（命中 k8s_component_name "main"）被交换到首位
        assertThat(containers.get(0).get("container_name")).isEqualTo("main-app");
        assertThat(containers.get(1).get("container_name")).isEqualTo("sidecar");
    }

    @Test
    void empty_bean_returns_empty_map() {
        wire("x");
        when(client.exchange(any(), any(), any(), any(), any(), any())).thenReturn("{\"bean\":{}}");
        assertThat(service.listPods("default", "a", "rainbond")).isEmpty();
    }
}
