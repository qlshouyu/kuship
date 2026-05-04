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

/** rainbond `tenant_services_port` —— 组件端口。 */
@Entity
@Table(name = "tenant_services_port")
@Getter
@Setter
@NoArgsConstructor
public class TenantServicesPort {

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

    @Column(name = "mapping_port")
    private Integer mappingPort;

    @Column(name = "lb_mapping_port")
    private Integer lbMappingPort;

    @Column(name = "protocol", length = 15)
    private String protocol;

    @Column(name = "port_alias", length = 64)
    private String portAlias;

    @Column(name = "is_inner_service")
    private Boolean innerService;

    @Column(name = "is_outer_service")
    private Boolean outerService;

    @Column(name = "k8s_service_name", length = 63)
    private String k8sServiceName;

    @Column(name = "name", length = 64)
    private String name;
}
