package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentReverseUndependencyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组件“可被依赖但未依赖”的组件列表读（对齐 rainbond AppDependencyReverseView.get）。 */
@RestController
public class ComponentReverseUndependencyController {

    private final ComponentReverseUndependencyService service;

    public ComponentReverseUndependencyController(ComponentReverseUndependencyService service) {
        this.service = service;
    }

    /** GET .../apps/{serviceAlias}/dependency-reverse?page=&page_size=&search_key=&condition= 。data={bean,list,total}。 */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/dependency-reverse")
    public ApiResult reverse(@PathVariable("tenantName") String tenantName,
                             @PathVariable("serviceAlias") String serviceAlias,
                             @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                             @RequestParam(value = "page_size", required = false, defaultValue = "25") int pageSize,
                             @RequestParam(value = "search_key", required = false) String searchKey,
                             @RequestParam(value = "condition", required = false) String condition,
                             @RequestParam(value = "region_name", required = false) String regionName) {
        ComponentReverseUndependencyService.Result r =
                service.list(tenantName, serviceAlias, page, pageSize, searchKey, condition);
        return GeneralMessage.list(200, "success", "查询成功", r.list()).putExtra("total", r.total());
    }
}
