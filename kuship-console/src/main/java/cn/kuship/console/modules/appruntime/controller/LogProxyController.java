package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceLogOperations;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code POST /log_proxy}：通用日志代理，body 含 region/path/query/body。 */
@RestController
public class LogProxyController {

    private final ServiceLogOperations log;
    private final RuntimeContextLoader loader;

    public LogProxyController(ServiceLogOperations log, RuntimeContextLoader loader) {
        this.log = log;
        this.loader = loader;
    }

    @PostMapping(value = {"/console/log_proxy", "/console/log_proxy/"})
    public ApiResult proxy(@RequestBody Map<String, Object> body) {
        // 鉴别请求来源 region/path —— 当 body 含 service_alias 时按组件维度查 log；其他场景透传给 region
        String teamName = String.valueOf(body.getOrDefault("team_name", ""));
        String serviceAlias = String.valueOf(body.getOrDefault("service_alias", ""));
        if (teamName.isEmpty() || serviceAlias.isEmpty()) {
            return GeneralMessage.ok(Map.of("logs", java.util.List.of()));
        }
        var s = loader.requireService(teamName, serviceAlias);
        Map<String, Object> req = new LinkedHashMap<>(body);
        req.remove("team_name");
        req.remove("service_alias");
        Map<String, Object> resp = log.getServiceLogs(s.getServiceRegion(), teamName, serviceAlias, req);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }
}
