package cn.kuship.console.modules.app.service;

import cn.kuship.console.modules.app.entity.AutoscalerRuleMetrics;
import cn.kuship.console.modules.app.entity.AutoscalerRules;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.AutoscalerRuleMetricsRepository;
import cn.kuship.console.modules.app.repository.AutoscalerRulesRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 伸缩规则：rule + 嵌套 metrics 分组、空。 */
class ComponentAutoscalerServiceTest {

    private final TenantServiceInfoRepository svcRepo = mock(TenantServiceInfoRepository.class);
    private final AutoscalerRulesRepository rulesRepo = mock(AutoscalerRulesRepository.class);
    private final AutoscalerRuleMetricsRepository metricsRepo = mock(AutoscalerRuleMetricsRepository.class);
    private final ComponentAutoscalerService service = new ComponentAutoscalerService(svcRepo, rulesRepo, metricsRepo);

    private TenantServiceInfo svc() {
        TenantServiceInfo s = mock(TenantServiceInfo.class);
        when(s.getServiceId()).thenReturn("sid");
        return s;
    }

    @Test
    @SuppressWarnings("unchecked")
    void rule_with_nested_metrics() {
        TenantServiceInfo s = svc();
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        AutoscalerRules rule = mock(AutoscalerRules.class);
        when(rule.getRuleId()).thenReturn("R1");
        java.util.Map<String, Object> rd = new java.util.LinkedHashMap<>();
        rd.put("rule_id", "R1"); rd.put("enable", true);
        when(rule.toDict()).thenReturn(rd);
        when(rulesRepo.findByServiceId("sid")).thenReturn(List.of(rule));
        AutoscalerRuleMetrics m = mock(AutoscalerRuleMetrics.class);
        when(m.getRuleId()).thenReturn("R1");
        when(m.toDict()).thenReturn(Map.of("rule_id", "R1", "metric_name", "cpu"));
        when(metricsRepo.findByRuleIdIn(List.of("R1"))).thenReturn(List.of(m));

        List<Map<String, Object>> res = service.listRules("a");
        assertThat(res).hasSize(1);
        assertThat(res.get(0)).containsEntry("rule_id", "R1").containsEntry("enable", true);
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) res.get(0).get("metrics");
        assertThat(metrics).hasSize(1).first().isEqualTo(Map.of("rule_id", "R1", "metric_name", "cpu"));
    }

    @Test
    void empty() {
        TenantServiceInfo s = svc();
        when(svcRepo.findByServiceAlias("a")).thenReturn(Optional.of(s));
        when(rulesRepo.findByServiceId("sid")).thenReturn(List.of());
        assertThat(service.listRules("a")).isEmpty();
    }
}
