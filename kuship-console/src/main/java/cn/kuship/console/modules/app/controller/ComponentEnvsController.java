package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentEnvsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 组件环境变量列表读（对齐 rainbond AppEnvView.get，AppBaseView 登录态）。 */
@RestController
public class ComponentEnvsController {

    private final ComponentEnvsService service;

    public ComponentEnvsController(ComponentEnvsService service) {
        this.service = service;
    }

    /** GET /console/teams/{tenantName}/apps/{serviceAlias}/envs?env_type=inner|outer&env_name=&page=&page_size= */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/envs")
    public ApiResult envs(@PathVariable("tenantName") String tenantName,
                          @PathVariable("serviceAlias") String serviceAlias,
                          @RequestParam(value = "env_type", required = false) String envType,
                          @RequestParam(value = "env_name", required = false) String envName,
                          @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                          @RequestParam(value = "page_size", required = false, defaultValue = "10") int pageSize) {
        ComponentEnvsService.Result r = service.listEnvs(tenantName, serviceAlias, envType, envName, page, pageSize);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("total", r.total());
        return GeneralMessage.bean(200, "success", "查询成功", bean).putExtra("list", r.list());
    }
}
