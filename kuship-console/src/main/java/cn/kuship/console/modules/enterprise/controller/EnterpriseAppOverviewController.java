package cn.kuship.console.modules.enterprise.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.enterprise.service.EnterpriseAppOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 企业应用运行概览（对齐 rainbond EnterpriseAppOverView）。
 * 无可用集群 → code 404「no found regions」；否则 200「success」+ bean{service_groups, components}。
 */
@RestController
public class EnterpriseAppOverviewController {

    private final EnterpriseAppOverviewService service;

    public EnterpriseAppOverviewController(EnterpriseAppOverviewService service) {
        this.service = service;
    }

    /** GET /console/enterprise/{enterprise_id}/overview/app */
    @GetMapping("/console/enterprise/{enterprise_id}/overview/app")
    public ApiResult overviewApp(@PathVariable("enterprise_id") String enterpriseId) {
        Map<String, Object> data = service.overview(enterpriseId);
        if (data == null) {
            return GeneralMessage.message(404, "no found regions", "查询成功");
        }
        return GeneralMessage.bean(200, "success", "查询成功", data);
    }
}
