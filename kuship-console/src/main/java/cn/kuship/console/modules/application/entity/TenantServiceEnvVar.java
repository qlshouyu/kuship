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

import java.time.LocalDateTime;

/** rainbond `tenant_service_env_var` —— 组件环境变量。 */
@Entity
@Table(name = "tenant_service_env_var")
@Getter
@Setter
@NoArgsConstructor
public class TenantServiceEnvVar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "service_id", length = 32)
    private String serviceId;

    @Column(name = "container_port")
    private Integer containerPort;

    @Column(name = "name", length = 1024)
    private String name;

    @Column(name = "attr_name", length = 1024)
    private String attrName;

    @Column(name = "attr_value", columnDefinition = "longtext")
    private String attrValue;

    @Column(name = "is_change")
    private Boolean change;

    @Column(name = "scope", length = 10)
    private String scope;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
