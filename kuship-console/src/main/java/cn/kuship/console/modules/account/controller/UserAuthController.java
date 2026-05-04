package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.dto.ChangePasswordReq;
import cn.kuship.console.modules.account.dto.LoginReq;
import cn.kuship.console.modules.account.dto.RegisterReq;
import cn.kuship.console.modules.account.dto.UserDetailDto;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.jwt.JwtIssuer;
import cn.kuship.console.modules.account.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code /console/users/login} / {@code logout} / {@code register} / {@code changepwd} 端点。 */
@RestController
@RequestMapping("/console/users")
public class UserAuthController {

    private final UserService userService;
    private final JwtIssuer jwtIssuer;
    private final RequestContext requestContext;

    public UserAuthController(UserService userService, JwtIssuer jwtIssuer, RequestContext requestContext) {
        this.userService = userService;
        this.jwtIssuer = jwtIssuer;
        this.requestContext = requestContext;
    }

    @PostMapping(value = {"/login", "/login/"})
    public ApiResult login(@RequestBody @Valid LoginReq req) {
        UserInfo user = userService.authenticate(req.nickName(), req.password());
        String token = jwtIssuer.issue(user);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("token", token);
        bean.put("user", UserDetailDto.from(user));
        return GeneralMessage.ok(bean);
    }

    @PostMapping(value = {"/logout", "/logout/"})
    public ApiResult logout() {
        // stateless JWT；服务端无 session，仅返回占位响应
        return new ApiResult(200, "logout success", "登出成功", Map.of("bean", Map.of(), "list", java.util.List.of()));
    }

    @PostMapping(value = {"/register", "/register/"})
    public ApiResult register(@RequestBody @Valid RegisterReq req) {
        UserInfo user = userService.register(req);
        String token = jwtIssuer.issue(user);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("token", token);
        bean.put("user", UserDetailDto.from(user));
        return GeneralMessage.ok(bean);
    }

    @PostMapping(value = {"/changepwd", "/changepwd/"})
    public ApiResult changePassword(@RequestBody @Valid ChangePasswordReq req) {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        userService.changePassword(userId, req.oldPassword(), req.newPassword());
        return GeneralMessage.ok();
    }
}
