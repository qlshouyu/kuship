package cn.kuship.console.modules.appcreate.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.appcreate.dto.ThirdPartyCreateReq;
import cn.kuship.console.modules.appcreate.service.AppCreateService;
import cn.kuship.console.modules.application.entity.TenantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code /console/teams/{team_name}/apps/third_party}：第三方组件（外部 endpoint）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/third_party")
public class AppThirdPartyCreateController {

    private final AppCreateService createService;
    private final RequestContext requestContext;
    private final JsonMapper json = JsonMapper.builder().build();

    public AppThirdPartyCreateController(AppCreateService createService, RequestContext requestContext) {
        this.createService = createService;
        this.requestContext = requestContext;
    }

    @PostMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_CREATE)
    public ApiResult create(@PathVariable("team_name") String teamName,
                              @RequestBody @Valid ThirdPartyCreateReq req) {
        Integer userId = requireUser();
        // 第三方组件不调 region createService（无需 K8s deployment）
        TenantService saved = createService.createComponent(
                teamName, req.groupId(), req.regionName(),
                (s, sid, alias) -> {
                    s.setServiceCname(req.serviceCname());
                    s.setServiceName(req.serviceCname());
                    s.setK8sComponentName(req.k8sComponentName() != null ? req.k8sComponentName() : alias);
                    s.setServiceOrigin("third_party");
                    s.setServiceSource("third_party");
                    s.setCategory("application");
                    s.setImage("");
                    s.setCmd("");
                    s.setExtendMethod("stateless");
                    s.setProtocol("");
                    s.setServicePort(0);
                    s.setInnerPort(0);
                    s.setLanguage("third_party");
                    s.setBuildStrategy("third_party");
                    s.setCodeFrom("third_party");
                    s.setArch("amd64");
                },
                source -> {
                    if (req.endpoints() != null) {
                        try {
                            String endpointsJson = json.writeValueAsString(req.endpoints());
                            // extend_info 限长 1024
                            source.setExtendInfo(endpointsJson.length() > 1024
                                    ? endpointsJson.substring(0, 1024) : endpointsJson);
                        } catch (Exception e) {
                            source.setExtendInfo("[]");
                        }
                    }
                    source.setGroupKey(req.kind() != null ? req.kind() : "static");
                },
                false,
                userId);
        return GeneralMessage.ok(serialize(saved));
    }

    private Integer requireUser() {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        return userId;
    }

    private Map<String, Object> serialize(TenantService s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service_id", s.getServiceId());
        m.put("service_alias", s.getServiceAlias());
        m.put("service_cname", s.getServiceCname());
        m.put("k8s_component_name", s.getK8sComponentName());
        m.put("service_source", "third_party");
        m.put("create_status", s.getCreateStatus());
        return m;
    }
}
