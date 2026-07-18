package cn.kuship.console.modules.enterprise.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.enterprise.service.ServiceAlarmService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业异常组件告警（对齐 rainbond ServiceAlarm）。msg 固定为「team query success」。
 */
@RestController
public class ServiceAlarmController {

    private final ServiceAlarmService service;

    public ServiceAlarmController(ServiceAlarmService service) {
        this.service = service;
    }

    /** GET /console/enterprise/{enterprise_id}/service_alarm */
    @GetMapping("/console/enterprise/{enterprise_id}/service_alarm")
    public ApiResult serviceAlarm(@PathVariable("enterprise_id") String enterpriseId) {
        return GeneralMessage.list(200, "team query success", "查询成功",
                service.listServiceAlarm(enterpriseId));
    }
}
