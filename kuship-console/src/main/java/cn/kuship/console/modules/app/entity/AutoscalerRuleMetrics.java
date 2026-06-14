package cn.kuship.console.modules.app.entity;

import jakarta.persistence.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** 映射既有 {@code autoscaler_rule_metrics}（伸缩规则指标）。只读 + to_dict(model 顺序)。 */
@Entity
@Table(name = "autoscaler_rule_metrics")
public class AutoscalerRuleMetrics {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "ID")
    private Integer id;
    @Column(name = "rule_id", length = 32) private String ruleId;
    @Column(name = "metric_type", length = 16) private String metricType;
    @Column(name = "metric_name", length = 255) private String metricName;
    @Column(name = "metric_target_type", length = 13) private String metricTargetType;
    @Column(name = "metric_target_value") private Integer metricTargetValue;

    public String getRuleId() { return ruleId; }

    public Map<String, Object> toDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", id);
        m.put("rule_id", ruleId);
        m.put("metric_type", metricType);
        m.put("metric_name", metricName);
        m.put("metric_target_type", metricTargetType);
        m.put("metric_target_value", metricTargetValue);
        return m;
    }
}
