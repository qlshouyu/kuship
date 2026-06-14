package cn.kuship.console.modules.app.entity;

import jakarta.persistence.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** 映射既有 {@code autoscaler_rules}（自动伸缩规则）。只读 + to_dict(model 顺序)。 */
@Entity
@Table(name = "autoscaler_rules")
public class AutoscalerRules {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "ID")
    private Integer id;
    @Column(name = "rule_id", length = 32) private String ruleId;
    @Column(name = "service_id", length = 32) private String serviceId;
    @Column(name = "enable") private Boolean enable;
    @Column(name = "xpa_type", length = 3) private String xpaType;
    @Column(name = "min_replicas") private Integer minReplicas;
    @Column(name = "max_replicas") private Integer maxReplicas;

    public String getRuleId() { return ruleId; }

    public Map<String, Object> toDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", id);
        m.put("rule_id", ruleId);
        m.put("service_id", serviceId);
        m.put("enable", enable);
        m.put("xpa_type", xpaType);
        m.put("min_replicas", minReplicas);
        m.put("max_replicas", maxReplicas);
        return m;
    }
}
