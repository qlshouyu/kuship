package cn.kuship.console.modules.account.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.dto.ConsoleConfigItem;
import cn.kuship.console.modules.account.service.CustomConfigsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 全局自定义配置端点（对齐 rainbond {@code CustomConfigsCLView}，BaseApiView 不要求 JWT）。
 *
 * <p>用户级 {@code /console/users/custom_configs} 在 {@link UserSelfController} 提供。
 */
@RestController
@RequestMapping("/console")
public class CustomConfigsController {

    private final CustomConfigsService customConfigsService;

    public CustomConfigsController(CustomConfigsService customConfigsService) {
        this.customConfigsService = customConfigsService;
    }

    @GetMapping(value = {"/custom_configs", "/custom_configs/"})
    public ApiResult getCustomConfigs() {
        List<ConsoleConfigItem> items = customConfigsService.list("").stream()
                .map(ConsoleConfigItem::from).toList();
        return GeneralMessage.okList(items);
    }

    @PutMapping(value = {"/custom_configs", "/custom_configs/"})
    public ApiResult putCustomConfigs(@RequestBody List<ConsoleConfigItem> items) {
        if (items == null) {
            throw new ServiceHandleException(400, "request body must be a list", "请求参数必须为列表");
        }
        List<ConsoleConfigItem> saved = customConfigsService.bulkCreateOrUpdate(items, "").stream()
                .map(ConsoleConfigItem::from).toList();
        return GeneralMessage.okList(saved);
    }
}
