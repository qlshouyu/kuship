package cn.kuship.console.modules.application.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.api.ServiceLabelOperations;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.entity.TenantServiceLabel;
import cn.kuship.console.modules.application.repository.TenantServiceLabelRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /console/teams/{team_name}/apps/{service_alias}/labels}：组件 node label 绑定 + 可用 label 列表。 */
@RestController
public class AppLabelController {

    private final ServiceLabelOperations labelOps;
    private final TenantServiceLabelRepository labelRepo;
    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;

    public AppLabelController(ServiceLabelOperations labelOps,
                              TenantServiceLabelRepository labelRepo,
                              TenantsRepository tenantsRepo,
                              TenantServiceRepository serviceRepo) {
        this.labelOps = labelOps;
        this.labelRepo = labelRepo;
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/labels",
                          "/console/teams/{team_name}/apps/{service_alias}/labels/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult listServiceLabels(@PathVariable("team_name") String teamName,
                                       @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(teamName, serviceAlias);
        List<TenantServiceLabel> rows = labelRepo.findByServiceId(s.getServiceId());
        return GeneralMessage.okList(rows.stream().map(AppLabelController::serialize).toList());
    }

    @PostMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/labels",
                           "/console/teams/{team_name}/apps/{service_alias}/labels/"})
    @RequirePerm(PermCode.APP_OVERVIEW_ENV)
    @Transactional
    public ApiResult addServiceLabels(@PathVariable("team_name") String teamName,
                                      @PathVariable("service_alias") String serviceAlias,
                                      @RequestBody Map<String, Object> body) {
        TenantService s = requireService(teamName, serviceAlias);
        Object idsObj = body == null ? null : body.get("label_ids");
        if (!(idsObj instanceof List<?> ids) || ids.isEmpty()) {
            throw new ServiceHandleException(400, "label_ids is empty", "标签 id 列表为空");
        }
        List<String> labelIds = ids.stream().map(String::valueOf).filter(x -> !x.isBlank()).toList();
        if (labelIds.isEmpty()) {
            throw new ServiceHandleException(400, "label_ids is empty", "标签 id 列表为空");
        }

        for (String labelId : labelIds) {
            if (labelRepo.findByServiceIdAndLabelId(s.getServiceId(), labelId).isEmpty()) {
                TenantServiceLabel l = new TenantServiceLabel();
                l.setTenantId(s.getTenantId());
                l.setServiceId(s.getServiceId());
                l.setLabelId(labelId);
                l.setRegion(s.getServiceRegion());
                labelRepo.save(l);
            }
        }
        labelOps.addServiceNodeLabel(s.getServiceRegion(), teamName, serviceAlias, body);
        return GeneralMessage.ok(Map.of("label_ids", labelIds));
    }

    @DeleteMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/labels",
                              "/console/teams/{team_name}/apps/{service_alias}/labels/"})
    @RequirePerm(PermCode.APP_OVERVIEW_ENV)
    @Transactional
    public ApiResult deleteServiceLabel(@PathVariable("team_name") String teamName,
                                        @PathVariable("service_alias") String serviceAlias,
                                        @RequestBody Map<String, Object> body) {
        TenantService s = requireService(teamName, serviceAlias);
        Object idObj = body == null ? null : body.get("label_id");
        if (idObj == null || idObj.toString().isBlank()) {
            throw new ServiceHandleException(400, "label_id is empty", "标签 id 为空");
        }
        String labelId = idObj.toString();

        try {
            labelOps.deleteServiceNodeLabel(s.getServiceRegion(), teamName, serviceAlias, body);
        } catch (RegionApiException e) {
            if (e.getHttpStatus() != 404) {
                throw e;
            }
        }
        labelRepo.deleteByServiceIdAndLabelId(s.getServiceId(), labelId);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{service_alias}/labels/available",
                          "/console/teams/{team_name}/apps/{service_alias}/labels/available/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult listAvailableLabels(@PathVariable("team_name") String teamName,
                                         @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(teamName, serviceAlias);
        try {
            List<Map<String, Object>> list = labelOps.listRegionLabels(s.getServiceRegion(), teamName);
            return GeneralMessage.okList(list);
        } catch (RegionApiException e) {
            return GeneralMessage.okList(List.of());
        }
    }

    private TenantService requireService(String teamName, String serviceAlias) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        return serviceRepo.findByTenantIdAndServiceAlias(team.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    private static Map<String, Object> serialize(TenantServiceLabel l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("label_id", l.getLabelId());
        m.put("service_id", l.getServiceId());
        m.put("region", l.getRegion());
        m.put("create_time", l.getCreateTime());
        return m;
    }
}
