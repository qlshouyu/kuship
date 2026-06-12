package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.security.JwtProperties;
import cn.kuship.console.modules.account.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录/登出接口，复刻 rainbond-console {@code ^users/login$}（JWTTokenView）。
 * 路径显式声明完整 {@code /console/...} 前缀，参数保持 snake_case。
 */
@RestController
public class UserLoginController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    public UserLoginController(AuthService authService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
    }

    /** POST /console/users/login  (form: nick_name, password) → data.bean.token */
    @PostMapping("/console/users/login")
    public ApiResult login(@RequestParam(value = "nick_name", required = false) String nickName,
                           @RequestParam(value = "password", required = false) String password,
                           HttpServletResponse response) {
        String token = authService.login(nickName, password);
        // 与 drf-jwt 一致：同时下发 cookie token（10 年）
        Cookie cookie = new Cookie(jwtProperties.getCookieName(), token);
        cookie.setPath("/");
        cookie.setMaxAge((int) Math.min(Integer.MAX_VALUE, jwtProperties.getExpirationDays() * 24 * 3600));
        response.addCookie(cookie);

        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("token", token);
        return GeneralMessage.bean(200, "login success", "登录成功", bean);
    }

    /** POST /console/users/logout → 将当前 token 加入黑名单 */
    @PostMapping("/console/users/logout")
    public ApiResult logout(HttpServletRequest request) {
        authService.logout(resolveToken(request));
        return GeneralMessage.message(200, "logout success", "已退出登录");
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && !header.isBlank()) {
            String[] parts = header.trim().split("\\s+", 2);
            if (parts.length == 2) {
                for (String prefix : jwtProperties.getHeaderPrefixes()) {
                    if (prefix.equalsIgnoreCase(parts[0])) {
                        return parts[1].trim();
                    }
                }
            }
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (jwtProperties.getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
