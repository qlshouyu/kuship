package cn.kuship.console.modules.region.controller.cluster;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ClusterOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 团队维度资源使用查询 endpoint。
 *
 * <p>区别于 {@code ClusterNamespacesController} 中的 {@code /enterprise/.../regions/{rid}/resource}
 * （企业管理员视角的集群总资源），本端点是普通团队成员查自己 tenant 的当前用量。
 */
@RestController
@RequestMapping("/console")
public class TenantResourcesController {

    private final ClusterOperations clusterOperations;
    private final RequestContext requestContext;

    public TenantResourcesController(ClusterOperations clusterOperations,
                                      RequestContext requestContext) {
        this.clusterOperations = clusterOperations;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"/teams/{team_name}/resources", "/teams/{team_name}/resources/"})
    @RequirePerm(PermCode.TEAM_REGION_DESCRIBE)
    public ApiResult tenantResources(@PathVariable("team_name") String teamName,
                                       @RequestParam("region_name") String regionName,
                                       @RequestParam(value = "enterprise_id", required = false) String enterpriseId) {
        if (enterpriseId == null || enterpriseId.isBlank()) {
            enterpriseId = requestContext.getEnterpriseId();
        }
        if (enterpriseId == null || enterpriseId.isBlank()) {
            throw new ServiceHandleException(401, "missing enterprise context", "缺少企业上下文");
        }
        Map<String, Object> bean = clusterOperations.getResources(regionName, teamName, enterpriseId);
        return GeneralMessage.ok(bean);
    }
}
