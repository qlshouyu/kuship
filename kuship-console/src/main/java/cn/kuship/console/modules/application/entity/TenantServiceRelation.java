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

/** rainbond `tenant_service_relation` —— 组件依赖关系。 */
@Entity
@Table(name = "tenant_service_relation")
@Getter
@Setter
@NoArgsConstructor
public class TenantServiceRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "service_id", length = 32)
    private String serviceId;

    @Column(name = "dep_service_id", length = 32)
    private String depServiceId;

    @Column(name = "dep_service_type", length = 50)
    private String depServiceType;

    @Column(name = "dep_order")
    private Integer depOrder;
}
