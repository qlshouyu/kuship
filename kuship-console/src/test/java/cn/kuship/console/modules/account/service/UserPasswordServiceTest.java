package cn.kuship.console.modules.account.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.util.PasswordEncryptor;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 改密：成功/旧错/不一致/新旧同/过短/默认密码 quirk。 */
class UserPasswordServiceTest {

    private final UserInfoRepository repo = mock(UserInfoRepository.class);
    private final UserPasswordService service = new UserPasswordService(repo);

    private static UserInfo user(String email, String rawPwd) {
        UserInfo u = new UserInfo();
        u.setUserId(700003);
        u.setEmail(email);
        u.setPassword(PasswordEncryptor.encrypt(email + rawPwd));
        return u;
    }

    @Test
    void change_success() {
        UserInfo u = user("v@k.cn", "Old@1234");
        service.changePassword(u, "Old@1234", "New@1234", "New@1234");
        assertThat(u.getPassword()).isEqualTo(PasswordEncryptor.encrypt("v@k.cn" + "New@1234"));
        verify(repo).save(u);
    }

    @Test
    void old_password_wrong() {
        UserInfo u = user("v@k.cn", "Old@1234");
        assertThatThrownBy(() -> service.changePassword(u, "WRONG", "New@1234", "New@1234"))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("old password");
        verify(repo, never()).save(u);
    }

    @Test
    void two_disagree() {
        UserInfo u = user("v@k.cn", "Old@1234");
        assertThatThrownBy(() -> service.changePassword(u, "Old@1234", "New@1234", "Diff@1234"))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("disagree");
    }

    @Test
    void old_equals_new() {
        UserInfo u = user("v@k.cn", "Old@1234");
        assertThatThrownBy(() -> service.changePassword(u, "Old@1234", "Old@1234", "Old@1234"))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("agree");
    }

    @Test
    void too_short() {
        UserInfo u = user("v@k.cn", "Old@1234");
        assertThatThrownBy(() -> service.changePassword(u, "Old@1234", "n2", "n2"))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("short");
    }

    @Test
    void default_password_quirk_allows_any_old() {
        // 库密码为 encrypt(email+"goodrain") → 旧密码校验恒过
        UserInfo u = user("v@k.cn", "goodrain");
        service.changePassword(u, "whatever", "New@1234", "New@1234");
        verify(repo).save(u);
    }
}
