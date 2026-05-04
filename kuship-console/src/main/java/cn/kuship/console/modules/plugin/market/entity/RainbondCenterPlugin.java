package cn.kuship.console.modules.plugin.market.entity;

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

/** rainbond `rainbond_center_plugin` —— 应用市场插件（含 longtext plugin_template + `desc` 反引号）。 */
@Entity
@Table(name = "rainbond_center_plugin")
@Getter
@Setter
@NoArgsConstructor
public class RainbondCenterPlugin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "plugin_key", length = 32, nullable = false)
    private String pluginKey;

    @Column(name = "plugin_name", length = 64, nullable = false)
    private String pluginName;

    @Column(name = "plugin_id", length = 32)
    private String pluginId;

    @Column(name = "category", length = 32, nullable = false)
    private String category;

    @Column(name = "record_id", nullable = false)
    private Integer recordId;

    @Column(name = "version", length = 20, nullable = false)
    private String version;

    @Column(name = "build_version", length = 32, nullable = false)
    private String buildVersion;

    @Column(name = "pic", length = 100)
    private String pic;

    @Column(name = "scope", length = 10, nullable = false)
    private String scope;

    @Column(name = "source", length = 15)
    private String source;

    @Column(name = "share_user", nullable = false)
    private Integer shareUser;

    @Column(name = "share_team", length = 32, nullable = false)
    private String shareTeam;

    @Column(name = "`desc`", length = 400)
    private String describe;

    @Column(name = "plugin_template", columnDefinition = "longtext", nullable = false)
    private String pluginTemplate;

    @Column(name = "is_complete", nullable = false)
    private Boolean isComplete;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "enterprise_id", length = 32, nullable = false)
    private String enterpriseId;

    @Column(name = "details", columnDefinition = "longtext")
    private String details;
}
