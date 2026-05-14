package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.gateway.service.GatewayContextLoader;
import cn.kuship.console.modules.gateway.service.GatewayPortService;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 可用 TCP 端口查询。
 * rainbond 锚点: {@code urls.py:661 GetPortView}
 * 路径: {@code /console/teams/{team_name}/domain/get_port}
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/domain/get_port",
        "/console/teams/{team_name}/domain/get_port/"
})
public class GetPortController {

    private final GatewayPortService portService;
    private final RegionInfoEntityRepository regionInfoRepo;

    public GetPortController(GatewayPortService portService,
                              RegionInfoEntityRepository regionInfoRepo) {
        this.portService = portService;
        this.regionInfoRepo = regionInfoRepo;
    }

    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public Map<String, Object> getPorts(@PathVariable("team_name") String teamName,
                                         @RequestParam(required = false) String region_name) {
        String regionId = "";
        if (region_name != null) {
            regionId = regionInfoRepo.findByRegionName(region_name)
                    .map(r -> r.getRegionId())
                    .orElse("");
        }
        List<Integer> ports = portService.getFreePorts(regionId);
        return Map.of("ports", ports, "count", ports.size());
    }
}
