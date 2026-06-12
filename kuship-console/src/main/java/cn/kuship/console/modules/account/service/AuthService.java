package cn.kuship.console.modules.account.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtService;
import cn.kuship.console.common.security.JwtVerifyException;
import cn.kuship.console.common.security.TokenBlacklistService;
import cn.kuship.console.common.util.PasswordEncryptor;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录/登出业务，复刻 rainbond-console {@code JWTTokenView} + {@code user_svc.is_exist} 的契约语义。
 */
@Service
public class AuthService {

    private final UserInfoRepository userRepository;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklist;

    public AuthService(UserInfoRepository userRepository, JwtService jwtService, TokenBlacklistService blacklist) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.blacklist = blacklist;
    }

    /**
     * 校验用户名/密码并签发 token。错误语义与 rainbond-console 一致：
     * 缺用户名/密码 → 400；用户不存在/密码错误 → 400 "authorization fail "。
     */
    @Transactional(readOnly = true)
    public String login(String nickName, String password) {
        if (nickName == null || nickName.isBlank()) {
            throw ServiceHandleException.badRequest("username is missing", "请填写用户名");
        }
        if (password == null || password.isBlank()) {
            throw ServiceHandleException.badRequest("password is missing", "请填写密码");
        }
        UserInfo user = userRepository.findByLoginName(nickName)
                .orElseThrow(() -> ServiceHandleException.badRequest("authorization fail ", "用户不存在"));
        if (!PasswordEncryptor.matches(user.getEmail(), password, user.getPassword())) {
            throw ServiceHandleException.badRequest("authorization fail ", "密码不正确");
        }
        return jwtService.generate(user);
    }

    /** 登出：把 token 写入 Redis 黑名单使其立即失效。 */
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        try {
            JwtClaims claims = jwtService.parse(rawToken);
            blacklist.blacklist(rawToken, claims.expiration());
        } catch (JwtVerifyException e) {
            // token 本就无效，无需拉黑
        }
    }
}
