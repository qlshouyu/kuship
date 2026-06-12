package cn.kuship.console.modules.app.service;

import cn.kuship.console.modules.app.entity.ServiceGroup;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 应用列表读：映射 group_id=ID / group_note=note / group_name；空列表。 */
class AppReadServiceTest {

    private final ServiceGroupRepository repo = mock(ServiceGroupRepository.class);
    private final AppReadService service = new AppReadService(repo);

    private static ServiceGroup app(int id, String name, String note) {
        ServiceGroup g = new ServiceGroup();
        g.setId(id);
        g.setGroupName(name);
        g.setNote(note);
        return g;
    }

    @Test
    void maps_three_fields() {
        when(repo.findByTenantIdAndRegionNameOrderByUpdateTimeDescOrderIndexDesc("t", "rainbond"))
                .thenReturn(List.of(app(5, "myapp", "备注"), app(6, "app2", null)));
        List<Map<String, Object>> list = service.listApps("t", "rainbond");
        assertThat(list).hasSize(2);
        assertThat(list.get(0)).containsEntry("group_name", "myapp").containsEntry("group_id", 5)
                .containsEntry("group_note", "备注");
        assertThat(list.get(1)).containsEntry("group_id", 6).containsEntry("group_note", null);
        assertThat(list.get(0).keySet()).containsExactly("group_name", "group_id", "group_note");
    }

    @Test
    void empty_when_no_apps() {
        when(repo.findByTenantIdAndRegionNameOrderByUpdateTimeDescOrderIndexDesc("t", "rainbond"))
                .thenReturn(List.of());
        assertThat(service.listApps("t", "rainbond")).isEmpty();
    }
}
