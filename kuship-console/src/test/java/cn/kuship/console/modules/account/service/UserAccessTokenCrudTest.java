package cn.kuship.console.modules.account.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.UserAccessKey;
import cn.kuship.console.modules.account.repository.UserAccessKeyRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 令牌 CRUD：create(note 空/成功 access_key 40hex)/getById 404/delete。 */
class UserAccessTokenCrudTest {

    private final UserAccessKeyRepository repo = mock(UserAccessKeyRepository.class);
    private final UserAccessTokenService service = new UserAccessTokenService(repo);

    @Test
    void create_requires_note() {
        assertThatThrownBy(() -> service.create(700002, "", 0))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("note");
        verify(repo, never()).save(any());
    }

    @Test
    void create_success_generates_40hex_key() {
        when(repo.save(any())).thenAnswer(i -> {
            UserAccessKey k = i.getArgument(0);
            k.setId(9);
            return k;
        });
        Map<String, Object> bean = service.create(700002, "ci", null);
        assertThat(bean).containsEntry("note", "ci").containsEntry("user_id", 700002)
                .containsEntry("expire_time", null).containsKey("access_key").containsKey("ID");
        assertThat((String) bean.get("access_key")).hasSize(40).matches("[0-9a-f]{40}");
    }

    @Test
    void create_with_age_sets_expire() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        Map<String, Object> bean = service.create(700002, "ci2", 3600);
        assertThat((Integer) bean.get("expire_time")).isGreaterThan(0);
    }

    @Test
    void get_by_id_404_when_missing() {
        when(repo.findByUserIdAndId(700002, 99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(700002, 99))
                .isInstanceOf(ServiceHandleException.class)
                .satisfies(e -> assertThat(((ServiceHandleException) e).getStatus()).isEqualTo(404));
    }

    @Test
    void delete_removes_when_present() {
        UserAccessKey k = new UserAccessKey();
        k.setId(9);
        when(repo.findByUserIdAndId(700002, 9)).thenReturn(Optional.of(k));
        service.delete(700002, 9);
        verify(repo).delete(k);
    }
}
