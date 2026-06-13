package cn.kuship.console.modules.app.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.app.service.ComponentPodsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组件实例列表读（对齐 rainbond ListAppPodsView.get，AppBaseView 登录态）。 */
@RestController
public class ComponentPodsController {

    private final ComponentPodsService service;

    public ComponentPodsController(ComponentPodsService service) {
        this.service = service;
    }

    /**
     * GET /console/teams/{tenantName}/apps/{serviceAlias}/pods?region_name=
     * data.list 是 dict {new_pods, old_pods}（非数组）；用 putExtra 覆盖默认 list。
     */
    @GetMapping("/console/teams/{tenantName}/apps/{serviceAlias}/pods")
    public ApiResult pods(@PathVariable("tenantName") String tenantName,
                          @PathVariable("serviceAlias") String serviceAlias,
                          @RequestParam(value = "region_name", required = false) String regionName) {
        return GeneralMessage.message(200, "success", "操作成功")
                .putExtra("list", service.listPods(tenantName, serviceAlias, regionName));
    }
}
