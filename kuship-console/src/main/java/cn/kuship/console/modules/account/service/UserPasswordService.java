package cn.kuship.console.modules.account.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.util.PasswordEncryptor;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import org.springframework.stereotype.Service;

/**
 * 修改登录密码（对齐 rainbond ChangeLoginPassword.post + update_password/check_user_password）。
 */
@Service
public class UserPasswordService {

    private static final String DEFAULT_PASSWORD = "goodrain";

    private final UserInfoRepository userInfoRepository;

    public UserPasswordService(UserInfoRepository userInfoRepository) {
        this.userInfoRepository = userInfoRepository;
    }

    public void changePassword(UserInfo user, String oldPassword, String newPassword, String newPassword2) {
        if (!checkUserPassword(user, oldPassword)) {
            throw ServiceHandleException.badRequest("old password error", "旧密码错误");
        }
        if (newPassword == null || !newPassword.equals(newPassword2)) {
            throw ServiceHandleException.badRequest("two password disagree", "两个密码不一致");
        }
        if (newPassword.equals(oldPassword)) {
            throw ServiceHandleException.badRequest("old and new password agree", "新旧密码一致");
        }
        if (newPassword.length() < 8) {
            throw ServiceHandleException.badRequest("password too short", "密码不能小于8位");
        }
        user.setPassword(PasswordEncryptor.encrypt(user.getEmail() + newPassword));
        userInfoRepository.save(user);
    }

    /** 对齐 check_user_password：库密码为默认（encrypt(email+"goodrain")）时恒过，否则比对旧密码。 */
    private boolean checkUserPassword(UserInfo user, String password) {
        if (PasswordEncryptor.matches(user.getEmail(), DEFAULT_PASSWORD, user.getPassword())) {
            return true;
        }
        return password != null && PasswordEncryptor.matches(user.getEmail(), password, user.getPassword());
    }
}
