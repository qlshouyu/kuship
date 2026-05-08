package cn.kuship.console.modules.misc.config.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.modules.misc.config.service.PlatformConfigService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点平台配置端点：复刻 rainbond {@code views/logos.py::ConfigRUDView}.
 *
 * <ul>
 *   <li>{@code GET /console/config/info} 公开匿名（{@code SecurityConfig} 已加 permitAll 白名单）</li>
 *   <li>{@code PUT /console/config/info?key=X} 仅 sys_admin（rainbond 端原本无校验，本次收口）</li>
 *   <li>{@code DELETE /console/config/info?key=X} 仅 sys_admin</li>
 * </ul>
 *
 * <p>响应不走自动包装（需要自定义 msg/msg_show + 顶层 initialize_info 占位），直接构造 {@link ApiResult}.
 */
@RestController
public class PlatformConfigController {

    private final PlatformConfigService service;
    private final RequestContext requestContext;

    public PlatformConfigController(PlatformConfigService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"/console/config/info", "/console/config/info/"})
    public ApiResult get() {
        Map<String, Object> bean = service.initializationOrGetConfig();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bean", bean);
        data.put("list", Collections.emptyList());
        data.put("initialize_info", Collections.emptyMap());
        return new ApiResult(200, "query success", "Logo获取成功", data);
    }

    @PutMapping(value = {"/console/config/info", "/console/config/info/"})
    public ApiResult put(@RequestParam(value = "key", required = false) String key,
                         @RequestBody(required = false) Map<String, Object> body) {
        requireSysAdmin();
        if (key == null || key.isBlank()) {
            return new ApiResult(404, "no found config key", "更新失败", emptyData());
        }
        if (body == null) body = Map.of();
        Object value = body.get("value");
        if (value == null) {
            return new ApiResult(404, "no found config value", "更新失败", emptyData());
        }
        boolean enable = parseEnable(body.get("enable"), true);
        Map<String, Object> bean = service.updateConfig(key, value, enable);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bean", bean);
        data.put("list", Collections.emptyList());
        return new ApiResult(200, "success", "更新成功", data);
    }

    @DeleteMapping(value = {"/console/config/info", "/console/config/info/"})
    public ApiResult delete(@RequestParam(value = "key", required = false) String key,
                            @RequestBody(required = false) Map<String, Object> body) {
        requireSysAdmin();
        if (key == null || key.isBlank()) {
            return new ApiResult(404, "no found config key", "重置失败", emptyData());
        }
        if (body == null || body.get("value") == null) {
            // rainbond 原版 delete 也要求 body 含 value 字段，否则 404
            return new ApiResult(404, "no found config value", "重置失败", emptyData());
        }
        Map<String, Object> bean = service.deleteConfig(key);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bean", bean);
        data.put("list", Collections.emptyList());
        return new ApiResult(200, "success", "重置成功", data);
    }

    private void requireSysAdmin() {
        if (!requestContext.isSysAdmin()) {
            throw new ServiceHandleException(403, "sys_admin required", "需要平台管理员权限");
        }
    }

    private static boolean parseEnable(Object raw, boolean fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Boolean b) return b;
        if (raw instanceof String s) return s.equalsIgnoreCase("true") || s.equals("1");
        return fallback;
    }

    private static Map<String, Object> emptyData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bean", Collections.emptyMap());
        data.put("list", Collections.emptyList());
        return data;
    }

    @SuppressWarnings("unused")
    private static List<?> placeholder() { return List.of(); }
}
