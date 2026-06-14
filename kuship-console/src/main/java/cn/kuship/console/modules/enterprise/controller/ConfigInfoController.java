package cn.kuship.console.modules.enterprise.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.enterprise.service.ConfigInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台公开配置（对齐 rainbond ConfigRUDView，AllowAny；登录页 bootstrap 依赖）。 */
@RestController
public class ConfigInfoController {

    private final ConfigInfoService service;

    public ConfigInfoController(ConfigInfoService service) {
        this.service = service;
    }

    /** GET /console/config/info （公开）。 */
    @GetMapping("/console/config/info")
    public ApiResult configInfo() {
        return GeneralMessage.bean(200, "query success", "Logo获取成功", service.getConfigInfo());
    }
}
