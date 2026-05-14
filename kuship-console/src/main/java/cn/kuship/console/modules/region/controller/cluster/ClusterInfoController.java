package cn.kuship.console.modules.region.controller.cluster;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ClusterOperations;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 集群基础信息 endpoint。region 端不支持 {@code /v2/cluster/info} 时由
 * {@code ClusterOperationsImpl} 内部降级为读本地 {@code region_info} entity。
 */
@RestController
@RequestMapping("/console")
public class ClusterInfoController {

    private final ClusterOperations clusterOperations;

    public ClusterInfoController(ClusterOperations clusterOperations) {
        this.clusterOperations = clusterOperations;
    }

    @GetMapping(value = {"/enterprise/{enterprise_id}/regions/{region_name}/info",
            "/enterprise/{enterprise_id}/regions/{region_name}/info/"})
    @RequireEnterpriseAdmin
    public ApiResult getClusterInfo(@PathVariable("enterprise_id") String enterpriseId,
                                      @PathVariable("region_name") String regionName) {
        Map<String, Object> bean = clusterOperations.getClusterInfo(regionName);
        return GeneralMessage.ok(bean);
    }
}
