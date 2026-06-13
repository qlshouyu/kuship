package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.service.UserCustomConfigsService;
import cn.kuship.console.common.exception.ServiceHandleException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;
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

    /** PUT /console/users/custom_configs (body 为 list) —— 批量创建/更新，回显入参。 */
    @PutMapping("/console/users/custom_configs")
    @SuppressWarnings("unchecked")
    public ApiResult bulkUpsert(@RequestBody(required = false) Object body) {
        if (!(body instanceof List<?> list)) {
            throw ServiceHandleException.badRequest("The request parameter must be a list", "请求参数必须为列表");
        }
        service.bulkCreateOrUpdate(requestContext.getCurrentUser().getNickName(), (List<Map<String, Object>>) (List<?>) list);
        return GeneralMessage.list(200, "success", "操作成功", list);
    }
}
