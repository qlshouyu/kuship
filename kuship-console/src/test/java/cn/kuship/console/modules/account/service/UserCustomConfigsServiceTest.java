package cn.kuship.console.modules.account.service;

import cn.kuship.console.modules.account.entity.ConsoleConfig;
import cn.kuship.console.modules.account.repository.ConsoleConfigRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 用户自定义配置：全列项/顺序、空。 */
class UserCustomConfigsServiceTest {

    private final ConsoleConfigRepository repo = mock(ConsoleConfigRepository.class);
    private final UserCustomConfigsService service = new UserCustomConfigsService(repo);

    @Test
    void maps_all_columns_in_order() {
        ConsoleConfig c = new ConsoleConfig();
        c.setId(3);
        c.setKey("theme");
        c.setValue("dark");
        c.setDescription("d");
        c.setUpdateTime(LocalDateTime.of(2026, 6, 13, 1, 2, 3));
        c.setUserNickName("interop");
        when(repo.findByUserNickName("interop")).thenReturn(List.of(c));
        List<Map<String, Object>> list = service.list("interop");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).keySet()).containsExactly("ID", "key", "value", "description", "update_time", "user_nick_name");
        assertThat(list.get(0)).containsEntry("ID", 3).containsEntry("key", "theme")
                .containsEntry("value", "dark").containsEntry("user_nick_name", "interop");
    }

    @Test
    void empty() {
        when(repo.findByUserNickName("x")).thenReturn(List.of());
        assertThat(service.list("x")).isEmpty();
    }
}
