package cn.kuship.console.modules.misc.audit.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.misc.audit.entity.LoginEvents;
import cn.kuship.console.modules.misc.audit.repository.LoginEventsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code GET /enterprise/{eid}/login-events} —— 登录事件查询。 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}")
public class LoginEventController {

    private final LoginEventsRepository repo;

    public LoginEventController(LoginEventsRepository repo) {
        this.repo = repo;
    }

    @GetMapping(value = {"/login-events", "/login-events/"})
    public ApiResult list(@PathVariable("enterprise_id") String enterpriseId,
                            @RequestParam(value = "page", defaultValue = "1") int page,
                            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 200) pageSize = 20;
        Page<LoginEvents> result = repo.findByEnterpriseIdOrderByLoginTimeDesc(
                enterpriseId, PageRequest.of(page - 1, pageSize));
        return GeneralMessage.ok(Map.of(
                "list", result.getContent().stream().map(LoginEventController::toBean).toList(),
                "total", result.getTotalElements()));
    }

    static Map<String, Object> toBean(LoginEvents e) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("event_id", e.getEventId());
        b.put("username", e.getUsername());
        b.put("login_time", e.getLoginTime());
        b.put("logout_time", e.getLogoutTime());
        b.put("duration", e.getDuration());
        b.put("client_ip", e.getClientIp());
        b.put("ip_locale_main", e.getIpLocaleMain());
        b.put("user_agent", e.getUserAgent());
        return b;
    }
}
