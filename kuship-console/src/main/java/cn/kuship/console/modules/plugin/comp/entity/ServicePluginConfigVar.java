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

import java.time.LocalDateTime;

/** rainbond `service_plugin_config_var` —— 组件挂载插件的实际填值（含 longtext attrs）。 */
@Entity
@Table(name = "service_plugin_config_var")
@Getter
@Setter
@NoArgsConstructor
public class ServicePluginConfigVar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "service_id", length = 32, nullable = false)
    private String serviceId;

    @Column(name = "plugin_id", length = 32, nullable = false)
    private String pluginId;

    @Column(name = "build_version", length = 32, nullable = false)
    private String buildVersion;

    @Column(name = "service_meta_type", length = 32, nullable = false)
    private String serviceMetaType;

    @Column(name = "injection", length = 32, nullable = false)
    private String injection;

    @Column(name = "dest_service_id", length = 32, nullable = false)
    private String destServiceId;

    @Column(name = "dest_service_alias", length = 32, nullable = false)
    private String destServiceAlias;

    @Column(name = "container_port", nullable = false)
    private Integer containerPort;

    @Column(name = "attrs", columnDefinition = "longtext", nullable = false)
    private String attrs;

    @Column(name = "protocol", length = 16, nullable = false)
    private String protocol;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
