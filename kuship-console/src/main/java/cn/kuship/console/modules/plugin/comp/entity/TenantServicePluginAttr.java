package cn.kuship.console.modules.plugin.comp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** rainbond `tenant_service_plugin_attr` —— 组件挂载插件的跨服务依赖属性（17 列）。 */
@Entity
@Table(name = "tenant_service_plugin_attr")
@Getter
@Setter
@NoArgsConstructor
public class TenantServicePluginAttr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "service_id", length = 32, nullable = false)
    private String serviceId;

    @Column(name = "service_alias", length = 32, nullable = false)
    private String serviceAlias;

    @Column(name = "dest_service_id", length = 32, nullable = false)
    private String destServiceId;

    @Column(name = "dest_service_alias", length = 32, nullable = false)
    private String destServiceAlias;

    @Column(name = "plugin_id", length = 32, nullable = false)
    private String pluginId;

    @Column(name = "service_meta_type", length = 32, nullable = false)
    private String serviceMetaType;

    @Column(name = "injection", length = 32, nullable = false)
    private String injection;

    @Column(name = "container_port", nullable = false)
    private Integer containerPort;

    @Column(name = "protocol", length = 16, nullable = false)
    private String protocol;

    @Column(name = "attr_name", length = 64, nullable = false)
    private String attrName;

    @Column(name = "attr_value", length = 128, nullable = false)
    private String attrValue;

    @Column(name = "attr_alt_value", length = 128, nullable = false)
    private String attrAltValue;

    @Column(name = "attr_type", length = 16, nullable = false)
    private String attrType;

    @Column(name = "attr_default_value", length = 128)
    private String attrDefaultValue;

    @Column(name = "is_change", nullable = false)
    private Boolean isChange;

    @Column(name = "attr_info", length = 32)
    private String attrInfo;
}
