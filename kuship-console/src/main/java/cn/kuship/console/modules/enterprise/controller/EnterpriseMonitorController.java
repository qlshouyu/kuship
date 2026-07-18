package cn.kuship.console.modules.enterprise.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.enterprise.service.EnterpriseMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 企业资源监控总览（对齐 rainbond EnterpriseMonitor）。
 * 无可用集群 → code 404「no found」msg_show=null；否则 200「success」msg_show=null + bean 资源汇总。
 */
@RestController
public class EnterpriseMonitorController {

    private final EnterpriseMonitorService service;

    public EnterpriseMonitorController(EnterpriseMonitorService service) {
        this.service = service;
    }

    /** GET /console/enterprise/{enterprise_id}/monitor */
    @GetMapping("/console/enterprise/{enterprise_id}/monitor")
    public ApiResult monitor(@PathVariable("enterprise_id") String enterpriseId) {
        Map<String, Object> data = service.monitor(enterpriseId);
        if (data == null) {
            return GeneralMessage.message(404, "no found", null);
        }
        return GeneralMessage.bean(200, "success", null, data);
    }
}
