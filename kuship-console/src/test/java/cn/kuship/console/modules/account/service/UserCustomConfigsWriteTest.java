package cn.kuship.console.modules.account.service;

import cn.kuship.console.modules.account.entity.ConsoleConfig;
import cn.kuship.console.modules.account.repository.ConsoleConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** custom_configs 批量写：create/update 分流、无 key skip。 */
class UserCustomConfigsWriteTest {

    private final ConsoleConfigRepository repo = mock(ConsoleConfigRepository.class);
    private final UserCustomConfigsService service = new UserCustomConfigsService(repo);

    private static ConsoleConfig existing(String key, String value) {
        ConsoleConfig c = new ConsoleConfig();
        c.setKey(key);
        c.setValue(value);
        c.setUserNickName("interop");
        return c;
    }

    @Test
    void create_when_key_absent_or_value_empty() {
        // theme 已存在且有值→update(删 key)；newk 不存在→create；空 key→skip
        when(repo.findByUserNickName("interop")).thenReturn(List.of(existing("theme", "dark")));
        service.bulkCreateOrUpdate("interop", List.of(
                Map.of("key", "theme", "value", "light"),
                Map.of("key", "newk", "value", "v"),
                Map.of("value", "novalue")));
        verify(repo).deleteByKeyIn(List.of("theme")); // 仅 theme(已存在且真值)入删
        verify(repo).saveAll(anyList());
    }

    @Test
    void no_delete_when_all_new() {
        when(repo.findByUserNickName("interop")).thenReturn(List.of());
        service.bulkCreateOrUpdate("interop", List.of(Map.of("key", "a", "value", "1")));
        verify(repo, never()).deleteByKeyIn(anyList());
        verify(repo).saveAll(anyList());
    }
}
