package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 企业用户列表：real_name 回退、query 过滤、分页、default_favorite null。 */
class EnterpriseUserReadServiceTest {

    private final UserInfoRepository repo = mock(UserInfoRepository.class);
    private final EnterpriseUserReadService service = new EnterpriseUserReadService(repo);

    private static UserInfo u(int id, String nick, String real, String email, String phone) {
        UserInfo x = new UserInfo();
        x.setUserId(id);
        x.setNickName(nick);
        x.setRealName(real);
        x.setEmail(email);
        x.setPhone(phone);
        return x;
    }

    @Test
    @SuppressWarnings("unchecked")
    void lists_with_real_name_fallback_and_nulls() {
        when(repo.findByEnterpriseId("e1")).thenReturn(List.of(
                u(700003, "viewer", "viewer", "viewer@k.cn", null),
                u(700002, "interop", null, "interop@k.cn", "139")));
        Map<String, Object> r = service.listUsers("e1", null, 1, 10);
        assertThat(r).containsEntry("total", 2).containsEntry("page", 1).containsEntry("page_size", 10);
        List<Map<String, Object>> list = (List<Map<String, Object>>) r.get("list");
        // 升序 user_id：700002 first
        assertThat(list.get(0)).containsEntry("user_id", 700002).containsEntry("real_name", "interop") // null→nick
                .containsEntry("default_favorite_name", null).containsEntry("default_favorite_url", null);
        assertThat(list.get(1)).containsEntry("user_id", 700003).containsEntry("real_name", "viewer");
    }

    @Test
    @SuppressWarnings("unchecked")
    void query_filters() {
        when(repo.findByEnterpriseId("e1")).thenReturn(List.of(
                u(1, "alice", null, "a@k.cn", null), u(2, "bob", null, "b@k.cn", null)));
        List<Map<String, Object>> list = (List<Map<String, Object>>) service.listUsers("e1", "ali", 1, 10).get("list");
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsEntry("nick_name", "alice");
    }

    @Test
    void paginates() {
        when(repo.findByEnterpriseId("e1")).thenReturn(List.of(
                u(1, "a", null, "a", null), u(2, "b", null, "b", null), u(3, "c", null, "c", null)));
        Map<String, Object> r = service.listUsers("e1", null, 2, 2);
        assertThat(r).containsEntry("total", 3);
        assertThat((List<?>) r.get("list")).hasSize(1); // page2 of size2 → 1 left
    }
}
