package cn.kuship.console.modules.account.service;

import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 用户模糊查询：nick/email icontains、user_id 升序、空 query_key。 */
class UserSearchServiceTest {

    private final UserInfoRepository repo = mock(UserInfoRepository.class);
    private final UserSearchService service = new UserSearchService(repo);

    private static UserInfo u(int id, String nick, String email) {
        UserInfo x = new UserInfo();
        x.setUserId(id);
        x.setNickName(nick);
        x.setEmail(email);
        return x;
    }

    @Test
    void matches_nick_or_email_case_insensitive_sorted() {
        when(repo.findAll()).thenReturn(List.of(
                u(700003, "viewer", "viewer@kuship.cn"), u(700002, "interop", "interop@kuship.cn")));
        // query 命中 email 含 kuship → 两个，user_id 升序
        List<Map<String, Object>> all = service.search("KUSHIP");
        assertThat(all).hasSize(2);
        assertThat(all.get(0)).containsEntry("user_id", 700002).containsEntry("nick_name", "interop")
                .containsEntry("email", "interop@kuship.cn");
        assertThat(all.get(1)).containsEntry("user_id", 700003);
        // query 命中 nick
        List<Map<String, Object>> one = service.search("inter");
        assertThat(one).hasSize(1);
        assertThat(one.get(0)).containsEntry("nick_name", "interop");
    }

    @Test
    void empty_query_returns_empty() {
        assertThat(service.search(null)).isEmpty();
        assertThat(service.search("")).isEmpty();
    }
}
