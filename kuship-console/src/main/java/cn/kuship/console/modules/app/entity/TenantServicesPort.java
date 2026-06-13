package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/** 映射既有 {@code tenant_services_port}（组件端口）。只读 + to_dict(model 顺序)。 */
@Entity
@Table(name = "tenant_services_port")
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
    private Boolean isInnerService;
    @Column(name = "is_outer_service")
    private Boolean isOuterService;
    @Column(name = "k8s_service_name", length = 63)
    private String k8sServiceName;
    @Column(name = "name", length = 64)
    private String name;

    /** BaseModel.to_dict（model 声明顺序）。 */
    public Map<String, Object> toDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", id);
        m.put("tenant_id", tenantId);
        m.put("service_id", serviceId);
        m.put("container_port", containerPort);
        m.put("mapping_port", mappingPort);
        m.put("lb_mapping_port", lbMappingPort);
        m.put("protocol", protocol);
        m.put("port_alias", portAlias);
        m.put("is_inner_service", isInnerService);
        m.put("is_outer_service", isOuterService);
        m.put("k8s_service_name", k8sServiceName);
        m.put("name", name);
        return m;
    }

    public Integer getContainerPort() { return containerPort; }
    public Integer getMappingPort() { return mappingPort; }
    public Integer getLbMappingPort() { return lbMappingPort; }
    public String getProtocol() { return protocol; }
    public boolean isInnerService() { return Boolean.TRUE.equals(isInnerService); }
    public boolean isOuterService() { return Boolean.TRUE.equals(isOuterService); }
}
