package cn.kuship.console.modules.plugin.team.entity;

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

/** rainbond `plugin_config_group` —— 插件版本下的配置组。 */
@Entity
@Table(name = "plugin_config_group")
@Getter
@Setter
@NoArgsConstructor
public class PluginConfigGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "plugin_id", length = 32, nullable = false)
    private String pluginId;

    @Column(name = "build_version", length = 32, nullable = false)
    private String buildVersion;

    @Column(name = "config_name", length = 32, nullable = false)
    private String configName;

    @Column(name = "service_meta_type", length = 32, nullable = false)
    private String serviceMetaType;

    @Column(name = "injection", length = 32, nullable = false)
    private String injection;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
