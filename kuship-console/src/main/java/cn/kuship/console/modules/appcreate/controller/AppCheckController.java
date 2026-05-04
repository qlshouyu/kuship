package cn.kuship.console.modules.appcreate.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code .../apps/{service_alias}/{check, get_check_uuid, check_update}} 三段式异步检查。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class AppCheckController {

    private final TenantServiceRepository serviceRepo;
    private final ServiceOperations serviceOperations;

    public AppCheckController(TenantServiceRepository serviceRepo, ServiceOperations serviceOperations) {
        this.serviceRepo = serviceRepo;
        this.serviceOperations = serviceOperations;
    }

    @PostMapping(value = {"/check", "/check/"})
    @RequirePerm(PermCode.APP_OVERVIEW_CREATE)
    @Transactional
    public ApiResult check(@PathVariable("team_name") String teamName,
                             @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(serviceAlias);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service_id", s.getServiceId());
        body.put("git_url", s.getGitUrl());
        body.put("code_version", s.getCodeVersion());
        body.put("language", s.getLanguage());
        Map<String, Object> resp = serviceOperations.codeCheck(s.getServiceRegion(), teamName, body);
        Object checkUuid = resp.get("check_uuid");
        if (checkUuid != null) {
            s.setCheckUuid(checkUuid.toString());
            Object eventId = resp.get("event_id");
            if (eventId != null) s.setCheckEventId(eventId.toString());
            serviceRepo.save(s);
        }
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("check_uuid", s.getCheckUuid());
        bean.put("event_id", s.getCheckEventId());
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/get_check_uuid", "/get_check_uuid/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult getCheckUuid(@PathVariable("team_name") String teamName,
                                    @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(serviceAlias);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("check_uuid", s.getCheckUuid());
        bean.put("event_id", s.getCheckEventId());
        return GeneralMessage.ok(bean);
    }

    @PutMapping(value = {"/check_update", "/check_update/"})
    @RequirePerm(PermCode.APP_OVERVIEW_CREATE)
    @Transactional
    public ApiResult checkUpdate(@PathVariable("team_name") String teamName,
                                   @PathVariable("service_alias") String serviceAlias,
                                   @RequestBody Map<String, Object> body) {
        TenantService s = requireService(serviceAlias);
        if (body.get("language") instanceof String l) s.setLanguage(l);
        if (body.get("image") instanceof String i) s.setImage(i);
        if (body.get("cmd") instanceof String c) s.setCmd(c);
        if (body.get("dockerfile") instanceof String d) s.setDockerfile(d);
        if (body.get("port") instanceof Number p) {
            s.setServicePort(p.intValue());
            s.setInnerPort(p.intValue());
        }
        // ports / envs 等推荐配置由前端单独 PUT 到 envs/ports 端点持久化（避免本端点过度复杂）
        serviceRepo.save(s);
        return GeneralMessage.ok(Map.of("service_alias", serviceAlias, "language", s.getLanguage()));
    }

    private TenantService requireService(String serviceAlias) {
        return serviceRepo.findAll().stream()
                .filter(s -> serviceAlias.equals(s.getServiceAlias()))
                .findFirst()
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }
}
