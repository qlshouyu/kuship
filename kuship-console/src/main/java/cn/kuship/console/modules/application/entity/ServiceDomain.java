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

/**
 * rainbond {@code service_domain} 表 —— HTTP 域名规则（19 列全字段映射）。
 *
 * <p>字段命名遵循 rainbond Python 历史拼写：{@code domain_heander}（不修正拼写），
 * {@code is_senior}（布尔），{@code auto_ssl_config}（varchar 32 nullable）等。
 */
@Entity
@Table(name = "service_domain")
@Getter
@Setter
@NoArgsConstructor
public class ServiceDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "service_id", length = 32)
    private String serviceId;

    @Column(name = "container_port")
    private Integer containerPort;

    @Column(name = "domain_name", length = 128)
    private String domainName;

    @Column(name = "protocol", length = 15)
    private String protocol;

    @Column(name = "domain_path", columnDefinition = "longtext")
    private String domainPath;

    /** HTTP 规则 ID（UUID）。 */
    @Column(name = "http_rule_id", length = 128, unique = true)
    private String httpRuleId;

    @Column(name = "region_id", length = 36)
    private String regionId;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "service_name", length = 64)
    private String serviceName;

    @Column(name = "service_alias", length = 64)
    private String serviceAlias;

    /** Cookie 匹配规则（longtext JSON）。 */
    @Column(name = "domain_cookie", columnDefinition = "longtext")
    private String domainCookie;

    /**
     * Header 匹配规则（longtext JSON）。
     * <b>保留 rainbond 历史拼写 {@code domain_heander}，不修正。</b>
     */
    @Column(name = "domain_heander", columnDefinition = "longtext")
    private String domainHeander;

    @Column(name = "certificate_id")
    private Integer certificateId;

    /** 域名类型（0=普通, 1=泛域名）。 */
    @Column(name = "domain_type")
    private String domainType;

    /** 是否高级路由（0=否, 1=是）。 */
    @Column(name = "is_senior")
    private Boolean isSenior;

    /** 规则类型（0=default, 1=自定义）。 */
    @Column(name = "type")
    private Integer type;

    /** 权重（0-100）。 */
    @Column(name = "the_weight")
    private Integer theWeight;

    /** 规则扩展信息（longtext JSON）。 */
    @Column(name = "rule_extensions", columnDefinition = "longtext")
    private String ruleExtensions;

    /** 是否对外开放。 */
    @Column(name = "is_outer_service")
    private Boolean isOuterService;

    /** 是否自动 SSL（DB 列类型 tinyint(1)，Hibernate 映射为 BIT/Boolean）。 */
    @Column(name = "auto_ssl")
    private Boolean autoSsl;

    /** 自动 SSL 配置（varchar 32 nullable）。 */
    @Column(name = "auto_ssl_config", length = 32)
    private String autoSslConfig;

    /** 路径重写开关。 */
    @Column(name = "path_rewrite")
    private Boolean pathRewrite;

    /** 重写规则集合（longtext JSON）。 */
    @Column(name = "rewrites", columnDefinition = "longtext")
    private String rewrites;
}
