package cn.kuship.console.modules.region.yaml.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.region.yaml.api.YamlResourceOperations;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** k8s yaml 资源解析与导入。 */
@RestController
public class YamlResourceController {

    private final YamlResourceOperations yamlOps;
    private final TenantsRepository tenantsRepo;

    public YamlResourceController(YamlResourceOperations yamlOps, TenantsRepository tenantsRepo) {
        this.yamlOps = yamlOps;
        this.tenantsRepo = tenantsRepo;
    }

    @PostMapping(value = {"/console/teams/{team_name}/resource-name",
                            "/console/teams/{team_name}/resource-name/"})
    @RequirePerm(PermCode.APP_OVERVIEW_CREATE)
    public ApiResult resourceName(@PathVariable("team_name") String teamName,
                                    @RequestParam(value = "region_name", required = false) String regionName,
                                    @RequestBody Map<String, Object> body) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        Map<String, Object> bean = yamlOps.yamlResourceName(team.getEnterpriseId(), regionName, body);
        return GeneralMessage.ok(bean);
    }

    @PostMapping(value = {"/console/teams/{team_name}/resource-detailed",
                            "/console/teams/{team_name}/resource-detailed/"})
    @RequirePerm(PermCode.APP_OVERVIEW_CREATE)
    public ApiResult resourceDetailed(@PathVariable("team_name") String teamName,
                                        @RequestParam(value = "region_name", required = false) String regionName,
                                        @RequestBody Map<String, Object> body) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        Map<String, Object> bean = yamlOps.yamlResourceDetailed(team.getEnterpriseId(), regionName, body);
        return GeneralMessage.ok(bean);
    }

    @PostMapping(value = {"/console/enterprise/{enterprise_id}/regions/{region_name}/yaml-resource-import",
                           "/console/enterprise/{enterprise_id}/regions/{region_name}/yaml-resource-import/"})
    public ApiResult resourceImport(@PathVariable("enterprise_id") String enterpriseId,
                                      @PathVariable("region_name") String regionName,
                                      @RequestBody Map<String, Object> body) {
        Map<String, Object> bean = yamlOps.yamlResourceImport(enterpriseId, regionName, body);
        return GeneralMessage.ok(bean);
    }
}
