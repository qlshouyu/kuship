package cn.kuship.console.modules.region.controller.cluster;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ClusterOperations;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 集群级事件 endpoint。所有 query 参数透传 region。
 */
@RestController
@RequestMapping("/console")
public class ClusterEventsController {

    private final ClusterOperations clusterOperations;

    public ClusterEventsController(ClusterOperations clusterOperations) {
        this.clusterOperations = clusterOperations;
    }

    @GetMapping(value = {"/enterprise/{enterprise_id}/regions/{region_name}/cluster-events",
            "/enterprise/{enterprise_id}/regions/{region_name}/cluster-events/"})
    @RequireEnterpriseAdmin
    public ApiResult clusterEvents(@PathVariable("enterprise_id") String enterpriseId,
                                     @PathVariable("region_name") String regionName,
                                     @RequestParam Map<String, String> queryParams) {
        Map<String, Object> body = new HashMap<>(queryParams);
        Map<String, Object> data = clusterOperations.getClusterEvents(regionName, body);
        return GeneralMessage.ok(data);
    }
}
