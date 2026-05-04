package cn.kuship.console.modules.appcreate.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.appcreate.dto.SourceCodeCreateReq;
import cn.kuship.console.modules.appcreate.service.AppCreateService;
import cn.kuship.console.modules.application.entity.TenantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code /console/teams/{team_name}/apps/source_code}：基于 Git 仓库创建组件。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/source_code")
public class AppSourceCodeCreateController {

    private final AppCreateService createService;
    private final RequestContext requestContext;

    public AppSourceCodeCreateController(AppCreateService createService, RequestContext requestContext) {
        this.createService = createService;
        this.requestContext = requestContext;
    }

    @PostMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_CREATE)
    public ApiResult create(@PathVariable("team_name") String teamName,
                              @RequestBody @Valid SourceCodeCreateReq req) {
        Integer userId = requireUser();
        TenantService saved = createService.createComponent(
                teamName, req.groupId(), req.regionName(),
                (s, sid, alias) -> {
                    s.setServiceCname(req.serviceCname());
                    s.setServiceName(req.serviceCname());
                    s.setK8sComponentName(req.k8sComponentName() != null ? req.k8sComponentName() : alias);
                    s.setGitUrl(req.gitUrl());
                    s.setCodeVersion(req.codeVersion() != null ? req.codeVersion() : "main");
                    s.setLanguage(req.language());
                    s.setDockerfile(req.dockerfile());
                    s.setBuildStrategy(req.buildStrategy() != null ? req.buildStrategy() : "buildpack");
                    s.setCodeFrom(req.gitUrl().contains("github") ? "github" : "git");
                    s.setOauthServiceId(req.oauthServiceId());
                    s.setMinCpu(req.minCpu() != null ? req.minCpu() : 120);
                    s.setMinMemory(req.minMemory() != null ? req.minMemory() : 128);
                    s.setTotalMemory(req.minMemory() != null ? req.minMemory() : 128);
                    s.setArch(req.arch() != null ? req.arch() : "amd64");
                    s.setServiceOrigin("assistant");
                    s.setServiceSource("source_code");
                    s.setCategory("application");
                    s.setImage("");
                    s.setExtendMethod("stateless");
                    s.setProtocol("");
                    s.setServicePort(0);
                    s.setInnerPort(0);
                },
                source -> {
                    source.setUserName(req.gitUsername());
                    source.setPassword(req.gitPassword());
                    source.setVersion(req.codeVersion());
                    source.setExtendInfo(req.gitUrl());
                },
                true,
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
        m.put("git_url", s.getGitUrl());
        m.put("code_version", s.getCodeVersion());
        m.put("build_strategy", s.getBuildStrategy());
        m.put("language", s.getLanguage());
        m.put("create_status", s.getCreateStatus());
        m.put("service_source", s.getServiceSource());
        return m;
    }
}
