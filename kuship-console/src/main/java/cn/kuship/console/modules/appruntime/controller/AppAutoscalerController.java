package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.appruntime.api.AutoscalerOperations;
import cn.kuship.console.modules.appruntime.service.AutoscalerRuleService;
import cn.kuship.console.modules.appruntime.service.RuntimeContextLoader;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 5 个端点：xparules（GET 列表 / POST 新建）、xparules/{rule_id}（GET / PUT）、xparecords。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class AppAutoscalerController {

    private final AutoscalerRuleService ruleService;
    private final AutoscalerOperations regionAutoscaler;
    private final RuntimeContextLoader loader;

    public AppAutoscalerController(AutoscalerRuleService ruleService,
                                     AutoscalerOperations regionAutoscaler,
                                     RuntimeContextLoader loader) {
        this.ruleService = ruleService;
        this.regionAutoscaler = regionAutoscaler;
        this.loader = loader;
    }

    @GetMapping(value = {"/xparules", "/xparules/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult list(@PathVariable("team_name") String teamName,
                            @PathVariable("service_alias") String alias) {
        TenantService s = loader.requireService(teamName, alias);
        List<Map<String, Object>> rules = ruleService.listByServiceId(s.getServiceId());
        return GeneralMessage.okList(rules);
    }

    @PostMapping(value = {"/xparules", "/xparules/"})
    @RequirePerm(PermCode.APP_OVERVIEW_TELESCOPIC)
    public ApiResult create(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String alias,
                              @RequestBody Map<String, Object> body) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> bean = ruleService.createRule(s, teamName, body);
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/xparules/{rule_id}", "/xparules/{rule_id}/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult get(@PathVariable("team_name") String teamName,
                            @PathVariable("service_alias") String alias,
                            @PathVariable("rule_id") String ruleId) {
        loader.requireService(teamName, alias);
        return GeneralMessage.ok(ruleService.getRule(ruleId));
    }

    @PutMapping(value = {"/xparules/{rule_id}", "/xparules/{rule_id}/"})
    @RequirePerm(PermCode.APP_OVERVIEW_TELESCOPIC)
    public ApiResult update(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String alias,
                              @PathVariable("rule_id") String ruleId,
                              @RequestBody Map<String, Object> body) {
        TenantService s = loader.requireService(teamName, alias);
        return GeneralMessage.ok(ruleService.updateRule(s, teamName, ruleId, body));
    }

    @GetMapping(value = {"/xparecords", "/xparecords/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult records(@PathVariable("team_name") String teamName,
                                @PathVariable("service_alias") String alias,
                                @RequestParam Map<String, String> queryParams) {
        TenantService s = loader.requireService(teamName, alias);
        Map<String, Object> resp = regionAutoscaler.listScalingRecords(s.getServiceRegion(), teamName, alias, queryParams);
        return GeneralMessage.ok(resp == null ? Map.of() : resp);
    }
}
