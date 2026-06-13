package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.service.UserAccessTokenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户访问令牌（对齐 rainbond UserAccessTokenCLView，JWTAuthApiView 仅登录，返回当前用户令牌）。 */
@RestController
public class UserAccessTokenController {

    private final UserAccessTokenService service;
    private final RequestContext requestContext;

    public UserAccessTokenController(UserAccessTokenService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    /** GET /console/users/access-token */
    @GetMapping("/console/users/access-token")
    public ApiResult list() {
        return GeneralMessage.list(200, "success", null, service.list(requestContext.getCurrentUser().getUserId()));
    }

    /** POST /console/users/access-token (form: note, age?) —— 创建令牌。 */
    @PostMapping("/console/users/access-token")
    public ApiResult create(@RequestParam(value = "note", required = false) String note,
                            @RequestParam(value = "age", required = false) Integer age) {
        return GeneralMessage.bean(200, null, null,
                service.create(requestContext.getCurrentUser().getUserId(), note, age));
    }
}
