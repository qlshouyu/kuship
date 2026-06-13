package cn.kuship.console.modules.account.service;

import cn.kuship.console.modules.account.entity.UserAccessKey;
import cn.kuship.console.modules.account.repository.UserAccessKeyRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 用户访问令牌列表：映射 {note,expire_time,user_id,ID}、空。 */
class UserAccessTokenServiceTest {

    private final UserAccessKeyRepository repo = mock(UserAccessKeyRepository.class);
    private final UserAccessTokenService service = new UserAccessTokenService(repo);

    @Test
    void maps_fields() {
        UserAccessKey k = new UserAccessKey();
        k.setId(5);
        k.setNote("ci");
        k.setUserId(700002);
        k.setExpireTime(null);
        when(repo.findByUserId(700002)).thenReturn(List.of(k));
        List<Map<String, Object>> list = service.list(700002);
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsEntry("note", "ci").containsEntry("expire_time", null)
                .containsEntry("user_id", 700002).containsEntry("ID", 5);
        assertThat(list.get(0).keySet()).containsExactly("note", "expire_time", "user_id", "ID");
    }

    @Test
    void empty() {
        when(repo.findByUserId(1)).thenReturn(List.of());
        assertThat(service.list(1)).isEmpty();
    }
}
