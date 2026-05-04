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

/** rainbond `plugin_config_items` —— 配置组下的具体配置项（含 longtext attr_alt_value/attr_default_value）。 */
@Entity
@Table(name = "plugin_config_items")
@Getter
@Setter
@NoArgsConstructor
public class PluginConfigItems {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "plugin_id", length = 32, nullable = false)
    private String pluginId;

    @Column(name = "build_version", length = 32, nullable = false)
    private String buildVersion;

    @Column(name = "service_meta_type", length = 32, nullable = false)
    private String serviceMetaType;

    @Column(name = "attr_name", length = 64, nullable = false)
    private String attrName;

    @Column(name = "attr_type", length = 16, nullable = false)
    private String attrType;

    @Column(name = "attr_alt_value", columnDefinition = "longtext", nullable = false)
    private String attrAltValue;

    @Column(name = "attr_default_value", columnDefinition = "longtext")
    private String attrDefaultValue;

    @Column(name = "is_change", nullable = false)
    private Boolean isChange;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "attr_info", length = 32)
    private String attrInfo;

    @Column(name = "protocol", length = 32)
    private String protocol;
}
