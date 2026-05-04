package cn.kuship.console.modules.appruntime.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * rainbond `autoscaler_rule_metrics` —— 单条 metric（cpu / memory / 自定义）。
 * 通过 rule_id 与 {@link AutoscalerRule} 逻辑关联（非 FK）。
 */
@Entity
@Table(name = "autoscaler_rule_metrics")
@Getter
@Setter
@NoArgsConstructor
public class AutoscalerRuleMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "rule_id", length = 32, nullable = false)
    private String ruleId;

    @Column(name = "metric_type", length = 16, nullable = false)
    private String metricType;

    @Column(name = "metric_name", length = 255, nullable = false)
    private String metricName;

    @Column(name = "metric_target_type", length = 13, nullable = false)
    private String metricTargetType;

    @Column(name = "metric_target_value", nullable = false)
    private Integer metricTargetValue;
}
