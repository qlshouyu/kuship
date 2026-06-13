package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.service.UserPasswordService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 修改登录密码（对齐 rainbond ChangeLoginPassword，JWTAuthApiView 仅登录，改当前用户密码）。 */
@RestController
public class UserPasswordController {

    private final UserPasswordService service;
    private final RequestContext requestContext;

    public UserPasswordController(UserPasswordService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    /** POST /console/users/changepwd (form: password/new_password/new_password2) */
    @PostMapping("/console/users/changepwd")
    public ApiResult changePassword(@RequestParam(value = "password", required = false) String password,
                                    @RequestParam(value = "new_password", required = false) String newPassword,
                                    @RequestParam(value = "new_password2", required = false) String newPassword2) {
        service.changePassword(requestContext.getCurrentUser(), password, newPassword, newPassword2);
        return GeneralMessage.message(200, "change password success", "密码修改成功");
    }
}
