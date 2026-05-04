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
 * rainbond `autoscaler_rules` —— 单组件自动伸缩规则。
 *
 * <p>PK Integer 自增，与 Django INT 4 字节对齐。
 */
@Entity
@Table(name = "autoscaler_rules")
@Getter
@Setter
@NoArgsConstructor
public class AutoscalerRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "rule_id", length = 32, nullable = false, unique = true)
    private String ruleId;

    @Column(name = "service_id", length = 32, nullable = false)
    private String serviceId;

    @Column(name = "enable", nullable = false)
    private Boolean enable;

    @Column(name = "xpa_type", length = 3, nullable = false)
    private String xpaType;

    @Column(name = "min_replicas")
    private Integer minReplicas;

    @Column(name = "max_replicas")
    private Integer maxReplicas;
}
