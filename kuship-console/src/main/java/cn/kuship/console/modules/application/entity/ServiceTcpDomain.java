package cn.kuship.console.modules.application.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** rainbond {@code service_tcp_domain} 表 —— TCP 端口暴露规则（14 列全字段映射）。 */
@Entity
@Table(name = "service_tcp_domain")
@Getter
@Setter
@NoArgsConstructor
public class ServiceTcpDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "service_id", length = 32)
    private String serviceId;

    @Column(name = "container_port")
    private Integer containerPort;

    @Column(name = "end_point", length = 256)
    private String endPoint;

    @Column(name = "is_outer_service")
    private Boolean outerService;

    /** TCP 规则 ID（UUID）。 */
    @Column(name = "tcp_rule_id", length = 128, unique = true)
    private String tcpRuleId;

    @Column(name = "region_id", length = 36)
    private String regionId;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "service_name", length = 64)
    private String serviceName;

    @Column(name = "service_alias", length = 64)
    private String serviceAlias;

    /** 协议（tcp / udp）。 */
    @Column(name = "protocol", length = 16)
    private String protocol;

    /** 规则类型。 */
    @Column(name = "type")
    private Integer type;

    /** 规则扩展信息（longtext JSON nullable）。 */
    @Column(name = "rule_extensions", columnDefinition = "longtext")
    private String ruleExtensions;
}
