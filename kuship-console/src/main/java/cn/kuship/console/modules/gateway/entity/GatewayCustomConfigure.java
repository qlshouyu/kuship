package cn.kuship.console.modules.gateway.entity;

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
 * rainbond {@code gateway_custom_configuration} 表 —— 高级路由参数持久化。
 *
 * <p>3 列：ID / rule_id（UNIQUE） / value（longtext JSON）。
 * value 字段存储 JSON 字符串（set_headers / connection_timeout / proxy_buffering 等 5.1+ 字段）。
 */
@Entity
@Table(name = "gateway_custom_configuration")
@Getter
@Setter
@NoArgsConstructor
public class GatewayCustomConfigure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "rule_id", length = 128, unique = true, nullable = false)
    private String ruleId;

    /** JSON 序列化的高级路由参数 Map。 */
    @Column(name = "value", columnDefinition = "longtext")
    private String value;
}
