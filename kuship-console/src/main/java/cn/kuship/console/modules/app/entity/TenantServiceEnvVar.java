package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 映射既有 {@code tenant_service_env_var}（组件环境变量）。只读。 */
@Entity
@Table(name = "tenant_service_env_var")
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
    private Boolean isChange;
    @Column(name = "scope", length = 10)
    private String scope;
    @Column(name = "create_time")
    private java.time.LocalDateTime createTime;

    public String getName() { return name; }
    public String getAttrName() { return attrName; }
    public String getAttrValue() { return attrValue; }

    /**
     * 对齐 AppEnvView 的 env_dict（raw cursor 顺序）：is_change 为**整数 0/1**（raw 游标返回 tinyint 为 int），
     * create_time 为 ISO 微秒（PyIsoDateTime：微秒不截尾零，匹配 DRF）。
     */
    public java.util.Map<String, Object> toEnvDict() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("ID", id);
        m.put("tenant_id", tenantId);
        m.put("service_id", serviceId);
        m.put("container_port", containerPort);
        m.put("name", name);
        m.put("attr_name", attrName);
        m.put("attr_value", attrValue);
        m.put("is_change", Boolean.TRUE.equals(isChange) ? 1 : 0);
        m.put("scope", scope);
        m.put("create_time", cn.kuship.console.common.util.PyIsoDateTime.iso(createTime));
        return m;
    }
}
