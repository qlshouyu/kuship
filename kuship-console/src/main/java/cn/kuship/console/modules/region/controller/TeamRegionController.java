package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.region.dto.OpenRegionReq;
import cn.kuship.console.modules.region.dto.RegionDto;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.service.TeamRegionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /console/teams/{team_name}/region} 开通/关闭/查询。 */
@RestController
@RequestMapping("/console/teams/{team_name}/region")
public class TeamRegionController {

    private final TeamRegionService teamRegionService;

    public TeamRegionController(TeamRegionService teamRegionService) {
        this.teamRegionService = teamRegionService;
    }

    @GetMapping(value = {"/query", "/query/"})
    @RequirePerm(PermCode.TEAM_REGION_DESCRIBE)
    public ApiResult query(@PathVariable("team_name") String teamName) {
        return GeneralMessage.okList(teamRegionService.listOpened(teamName).stream()
                .map(this::serialize).toList());
    }

    @GetMapping(value = {"/unopen", "/unopen/"})
    @RequirePerm(PermCode.TEAM_REGION_DESCRIBE)
    public ApiResult unopen(@PathVariable("team_name") String teamName) {
        return GeneralMessage.okList(teamRegionService.listUnopened(teamName).stream()
                .map(this::serialize).toList());
    }

    @PostMapping(value = {"", "/"})
    @RequirePerm(PermCode.TEAM_REGION_INSTALL)
    public ApiResult open(@PathVariable("team_name") String teamName,
                            @RequestBody @Valid OpenRegionReq req) {
        boolean opened = teamRegionService.openRegion(teamName, req.regionName());
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("opened", opened);
        bean.put("region_name", req.regionName());
        if (!opened) {
            return new cn.kuship.console.common.response.ApiResult(200, "already opened", "已开通该集群",
                    Map.of("bean", bean, "list", List.of()));
        }
        return GeneralMessage.ok(bean);
    }

    private Map<String, Object> serialize(RegionInfo r) {
        RegionDto dto = RegionDto.fromSafe(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("region_id", dto.regionId());
        m.put("region_name", dto.regionName());
        m.put("region_alias", dto.regionAlias());
        m.put("region_type", dto.regionType());
        m.put("url", dto.url());
        m.put("status", dto.status());
        m.put("scope", dto.scope());
        return m;
    }
}
