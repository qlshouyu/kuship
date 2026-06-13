package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 映射既有 {@code tenant_service}（Django TenantServiceInfo，组件）。只读，仅取状态读所需列。 */
@Entity
@Table(name = "tenant_service")
public class TenantServiceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "service_id", length = 32)
    private String serviceId;

    @Column(name = "service_alias", length = 100)
    private String serviceAlias;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "service_region", length = 64)
    private String serviceRegion;

    @Column(name = "check_uuid", length = 36)
    private String checkUuid;

    @Column(name = "extend_method", length = 32)
    private String extendMethod;

    @Column(name = "k8s_component_name", length = 64)
    private String k8sComponentName;

    public Integer getId() { return id; }
    public String getServiceId() { return serviceId; }
    public String getServiceAlias() { return serviceAlias; }
    public String getTenantId() { return tenantId; }
    public String getServiceRegion() { return serviceRegion; }
    public String getCheckUuid() { return checkUuid; }
    public String getExtendMethod() { return extendMethod; }
    public String getK8sComponentName() { return k8sComponentName; }
}
