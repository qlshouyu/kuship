package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.service.UserAccessTokenService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户访问令牌 查看/重生成/删除（对齐 rainbond UserAccessTokenRUDView，JWTAuthApiView 仅登录）。 */
@RestController
public class UserAccessTokenRUDController {

    private final UserAccessTokenService service;
    private final RequestContext requestContext;

    public UserAccessTokenRUDController(UserAccessTokenService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    @GetMapping("/console/users/access-token/{id}")
    public ApiResult get(@PathVariable("id") Integer id) {
        return GeneralMessage.bean(200, "success", null, service.getById(requestContext.getCurrentUser().getUserId(), id));
    }

    @PutMapping("/console/users/access-token/{id}")
    public ApiResult regenerate(@PathVariable("id") Integer id) {
        return GeneralMessage.bean(200, "success", null, service.regenerate(requestContext.getCurrentUser().getUserId(), id));
    }

    @DeleteMapping("/console/users/access-token/{id}")
    public ApiResult delete(@PathVariable("id") Integer id) {
        service.delete(requestContext.getCurrentUser().getUserId(), id);
        return GeneralMessage.message(200, "success", null);
    }
}
