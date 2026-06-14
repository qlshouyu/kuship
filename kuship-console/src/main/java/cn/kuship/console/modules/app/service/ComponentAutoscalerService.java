package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.app.entity.AutoscalerRuleMetrics;
import cn.kuship.console.modules.app.entity.AutoscalerRules;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.AutoscalerRuleMetricsRepository;
import cn.kuship.console.modules.app.repository.AutoscalerRulesRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件自动伸缩规则列表读（对齐 ListAppAutoscalerView.get + list_autoscaler_rules，纯 DB）。
 * 每 rule.to_dict + metrics:[按 rule_id 分组的 metric.to_dict]。
 */
@Service
public class ComponentAutoscalerService {

    private final TenantServiceInfoRepository serviceRepository;
    private final AutoscalerRulesRepository rulesRepository;
    private final AutoscalerRuleMetricsRepository metricsRepository;

    public ComponentAutoscalerService(TenantServiceInfoRepository serviceRepository,
                                      AutoscalerRulesRepository rulesRepository,
                                      AutoscalerRuleMetricsRepository metricsRepository) {
        this.serviceRepository = serviceRepository;
        this.rulesRepository = rulesRepository;
        this.metricsRepository = metricsRepository;
    }

    public List<Map<String, Object>> listRules(String serviceAlias) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        List<AutoscalerRules> rules = rulesRepository.findByServiceId(service.getServiceId());
        List<String> ruleIds = rules.stream().map(AutoscalerRules::getRuleId).toList();

        Map<String, List<Map<String, Object>>> r2m = new LinkedHashMap<>();
        if (!ruleIds.isEmpty()) {
            for (AutoscalerRuleMetrics metric : metricsRepository.findByRuleIdIn(ruleIds)) {
                r2m.computeIfAbsent(metric.getRuleId(), k -> new ArrayList<>()).add(metric.toDict());
            }
        }
        List<Map<String, Object>> res = new ArrayList<>();
        for (AutoscalerRules rule : rules) {
            Map<String, Object> r = rule.toDict();
            r.put("metrics", r2m.getOrDefault(rule.getRuleId(), new ArrayList<>()));
            res.add(r);
        }
        return res;
    }
}
