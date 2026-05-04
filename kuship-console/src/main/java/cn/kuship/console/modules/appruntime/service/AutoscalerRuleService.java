package cn.kuship.console.modules.appruntime.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.appruntime.api.AutoscalerOperations;
import cn.kuship.console.modules.appruntime.entity.AutoscalerRule;
import cn.kuship.console.modules.appruntime.entity.AutoscalerRuleMetric;
import cn.kuship.console.modules.appruntime.repository.AutoscalerRuleMetricRepository;
import cn.kuship.console.modules.appruntime.repository.AutoscalerRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 弹性伸缩规则双写服务：本地 autoscaler_rules + autoscaler_rule_metrics 与 region 同步。 */
@Service
public class AutoscalerRuleService {

    private final AutoscalerRuleRepository ruleRepo;
    private final AutoscalerRuleMetricRepository metricRepo;
    private final AutoscalerOperations regionAutoscaler;

    public AutoscalerRuleService(AutoscalerRuleRepository ruleRepo,
                                   AutoscalerRuleMetricRepository metricRepo,
                                   AutoscalerOperations regionAutoscaler) {
        this.ruleRepo = ruleRepo;
        this.metricRepo = metricRepo;
        this.regionAutoscaler = regionAutoscaler;
    }

    @Transactional
    public Map<String, Object> createRule(TenantService s, String teamName, Map<String, Object> body) {
        validate(body);
        String ruleId = UuidGenerator.makeUuid();
        AutoscalerRule rule = new AutoscalerRule();
        rule.setRuleId(ruleId);
        rule.setServiceId(s.getServiceId());
        rule.setEnable(parseBool(body.get("enable"), true));
        rule.setXpaType(stringOrDefault(body.get("xpa_type"), "hpa"));
        rule.setMinReplicas(parseInt(body.get("min_replicas"), 1));
        rule.setMaxReplicas(parseInt(body.get("max_replicas"), 1));
        ruleRepo.save(rule);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) body.getOrDefault("metrics", List.of());
        for (Map<String, Object> m : metrics) {
            AutoscalerRuleMetric metric = new AutoscalerRuleMetric();
            metric.setRuleId(ruleId);
            metric.setMetricType(stringOrDefault(m.get("metric_type"), "resource_metrics"));
            metric.setMetricName(stringOrDefault(m.get("metric_name"), "cpu"));
            metric.setMetricTargetType(stringOrDefault(m.get("metric_target_type"), "utilization"));
            metric.setMetricTargetValue(parseInt(m.get("metric_target_value"), 0));
            metricRepo.save(metric);
        }

        Map<String, Object> regionBody = new LinkedHashMap<>(body);
        regionBody.put("rule_id", ruleId);
        regionBody.put("service_id", s.getServiceId());
        regionAutoscaler.createRule(s.getServiceRegion(), teamName, s.getServiceAlias(), regionBody);
        return toBean(rule, metricRepo.findByRuleId(ruleId));
    }

    @Transactional
    public Map<String, Object> updateRule(TenantService s, String teamName, String ruleId, Map<String, Object> body) {
        validate(body);
        AutoscalerRule rule = ruleRepo.findByRuleId(ruleId)
                .orElseThrow(() -> new ServiceHandleException(404, "rule not found", "规则不存在"));
        rule.setEnable(parseBool(body.get("enable"), rule.getEnable()));
        rule.setMinReplicas(parseInt(body.get("min_replicas"), rule.getMinReplicas()));
        rule.setMaxReplicas(parseInt(body.get("max_replicas"), rule.getMaxReplicas()));
        ruleRepo.save(rule);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) body.get("metrics");
        if (metrics != null) {
            metricRepo.deleteByRuleId(ruleId);
            for (Map<String, Object> m : metrics) {
                AutoscalerRuleMetric metric = new AutoscalerRuleMetric();
                metric.setRuleId(ruleId);
                metric.setMetricType(stringOrDefault(m.get("metric_type"), "resource_metrics"));
                metric.setMetricName(stringOrDefault(m.get("metric_name"), "cpu"));
                metric.setMetricTargetType(stringOrDefault(m.get("metric_target_type"), "utilization"));
                metric.setMetricTargetValue(parseInt(m.get("metric_target_value"), 0));
                metricRepo.save(metric);
            }
        }

        Map<String, Object> regionBody = new LinkedHashMap<>(body);
        regionBody.put("rule_id", ruleId);
        regionAutoscaler.updateRule(s.getServiceRegion(), teamName, s.getServiceAlias(), ruleId, regionBody);
        return toBean(rule, metricRepo.findByRuleId(ruleId));
    }

    @Transactional
    public void deleteRule(TenantService s, String teamName, String ruleId) {
        AutoscalerRule rule = ruleRepo.findByRuleId(ruleId)
                .orElseThrow(() -> new ServiceHandleException(404, "rule not found", "规则不存在"));
        regionAutoscaler.deleteRule(s.getServiceRegion(), teamName, s.getServiceAlias(), ruleId);
        metricRepo.deleteByRuleId(ruleId);
        ruleRepo.delete(rule);
    }

    public Map<String, Object> getRule(String ruleId) {
        AutoscalerRule rule = ruleRepo.findByRuleId(ruleId)
                .orElseThrow(() -> new ServiceHandleException(404, "rule not found", "规则不存在"));
        return toBean(rule, metricRepo.findByRuleId(ruleId));
    }

    public List<Map<String, Object>> listByServiceId(String serviceId) {
        List<AutoscalerRule> rules = ruleRepo.findByServiceId(serviceId);
        List<Map<String, Object>> result = new ArrayList<>(rules.size());
        for (AutoscalerRule rule : rules) {
            result.add(toBean(rule, metricRepo.findByRuleId(rule.getRuleId())));
        }
        return result;
    }

    private static Map<String, Object> toBean(AutoscalerRule rule, List<AutoscalerRuleMetric> metrics) {
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("rule_id", rule.getRuleId());
        bean.put("service_id", rule.getServiceId());
        bean.put("enable", rule.getEnable());
        bean.put("xpa_type", rule.getXpaType());
        bean.put("min_replicas", rule.getMinReplicas());
        bean.put("max_replicas", rule.getMaxReplicas());
        List<Map<String, Object>> ms = new ArrayList<>(metrics.size());
        for (AutoscalerRuleMetric m : metrics) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("metric_type", m.getMetricType());
            mm.put("metric_name", m.getMetricName());
            mm.put("metric_target_type", m.getMetricTargetType());
            mm.put("metric_target_value", m.getMetricTargetValue());
            ms.add(mm);
        }
        bean.put("metrics", ms);
        return bean;
    }

    @SuppressWarnings("unchecked")
    private static void validate(Map<String, Object> body) {
        Integer min = parseInt(body.get("min_replicas"), 1);
        Integer max = parseInt(body.get("max_replicas"), 1);
        if (min < 1) throw new ServiceHandleException(400, "min_replicas must >=1", "最小副本数必须 >= 1");
        if (max < 1 || max > 65535) throw new ServiceHandleException(400, "max_replicas range [1,65535]", "最大副本数范围 [1,65535]");
        if (min > max) throw new ServiceHandleException(400, "min_replicas > max_replicas", "最小副本数不能大于最大副本数");
        Object metricsObj = body.get("metrics");
        if (metricsObj == null || !(metricsObj instanceof List<?> ml) || ml.isEmpty()) {
            throw new ServiceHandleException(400, "metrics required", "metrics 不能为空");
        }
        for (Object o : ml) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Integer v = parseInt(m.get("metric_target_value"), -1);
            if (v < 0 || v > 65535) {
                throw new ServiceHandleException(400, "metric_target_value range [0,65535]", "metric 阈值范围 [0,65535]");
            }
        }
    }

    private static String stringOrDefault(Object o, String d) {
        return o == null ? d : o.toString();
    }

    private static Integer parseInt(Object o, Integer d) {
        if (o == null) return d;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); }
        catch (NumberFormatException e) { return d; }
    }

    private static Boolean parseBool(Object o, Boolean d) {
        if (o == null) return d;
        if (o instanceof Boolean b) return b;
        return Boolean.parseBoolean(o.toString());
    }
}
