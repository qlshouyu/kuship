package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.service.UserCustomConfigsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户自定义配置（对齐 rainbond CustomConfigsUserCLView，JWTAuthApiView 仅登录，按当前用户 nick_name）。 */
@RestController
public class UserCustomConfigsController {

    private final UserCustomConfigsService service;
    private final RequestContext requestContext;

    public UserCustomConfigsController(UserCustomConfigsService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    /** GET /console/users/custom_configs */
    @GetMapping("/console/users/custom_configs")
    public ApiResult list() {
        return GeneralMessage.list(200, "success", "操作成功", service.list(requestContext.getCurrentUser().getNickName()));
    }
}
