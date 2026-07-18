package cn.kuship.console.modules.platform.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.platform.service.CustomConfigsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台级自定义配置（对齐 rainbond CustomConfigsCLView，BaseApiView）。
 * 区别于 {@code /console/users/custom_configs}（按当前用户 nick_name），本接口取 user_nick_name="" 的平台配置。
 */
@RestController
public class CustomConfigsController {

    private final CustomConfigsService service;

    public CustomConfigsController(CustomConfigsService service) {
        this.service = service;
    }

    /** GET /console/custom_configs */
    @GetMapping("/console/custom_configs")
    public ApiResult list() {
        return GeneralMessage.list(200, "success", "操作成功", service.list());
    }
}
