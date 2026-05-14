package cn.kuship.console.modules.application.controller.version;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 团队级批量查询组件 deploy_version：
 * {@code POST /console/teams/{team_name}/deploy-version} body {@code {service_ids: [...]}}。
 *
 * <p>region_name 通过 service_ids 中第一个组件的 service_region 推断。
 */
@RestController
public class BatchDeployVersionController {

    private final ServiceOperations serviceOps;
    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;

    public BatchDeployVersionController(ServiceOperations serviceOps,
                                          TenantsRepository tenantsRepo,
                                          TenantServiceRepository serviceRepo) {
        this.serviceOps = serviceOps;
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
    }

    @PostMapping(value = {"/console/teams/{team_name}/deploy-version",
                            "/console/teams/{team_name}/deploy-version/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult batchDeployVersion(@PathVariable("team_name") String teamName,
                                            @RequestBody Map<String, Object> body) {
        Tenants tenant = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));

        Object idsObj = body == null ? null : body.get("service_ids");
        if (!(idsObj instanceof List<?> ids) || ids.isEmpty()) {
            throw new ServiceHandleException(400, "missing service_ids", "缺少 service_ids");
        }

        String regionName = (body.get("region_name") instanceof String rn && !rn.isBlank()) ? rn : null;
        if (regionName == null) {
            String firstId = String.valueOf(ids.get(0));
            List<TenantService> services = serviceRepo.findByServiceIdIn(List.of(firstId));
            if (services.isEmpty()) {
                throw new ServiceHandleException(404, "service not found", "组件不存在");
            }
            regionName = services.get(0).getServiceRegion();
        }

        return GeneralMessage.ok(serviceOps.getTeamServicesDeployVersion(regionName, teamName, body));
    }
}
