package cn.kuship.console.modules.gateway.controller;

import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.gateway.service.GatewayPortService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 高级路由访问地址。
 * rainbond 锚点: {@code urls.py:657 GetSeniorUrlView}
 * 路径: {@code /console/teams/{team_name}/domain/get_senior_url}
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/domain/get_senior_url",
        "/console/teams/{team_name}/domain/get_senior_url/"
})
public class GetSeniorUrlController {

    private final GatewayPortService portService;

    public GetSeniorUrlController(GatewayPortService portService) {
        this.portService = portService;
    }

    @GetMapping
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public Map<String, Object> getSeniorUrl(@PathVariable("team_name") String teamName,
                                             @RequestParam(required = false) String region_name) {
        String url = (region_name != null) ? portService.getSeniorUrl(region_name) : "";
        return Map.of("senior_url", url);
    }
}
