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

/** rainbond `tenant_plugin_share` —— 插件分享记录（含 varchar(4096) config + `desc` 反引号）。 */
@Entity
@Table(name = "tenant_plugin_share")
@Getter
@Setter
@NoArgsConstructor
public class TenantPluginShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "share_id", length = 32, nullable = false)
    private String shareId;

    @Column(name = "share_version", length = 32, nullable = false)
    private String shareVersion;

    @Column(name = "origin_plugin_id", length = 32, nullable = false)
    private String originPluginId;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "`desc`", length = 256, nullable = false)
    private String describe;

    @Column(name = "plugin_name", length = 32, nullable = false)
    private String pluginName;

    @Column(name = "plugin_alias", length = 32, nullable = false)
    private String pluginAlias;

    @Column(name = "category", length = 32, nullable = false)
    private String category;

    @Column(name = "image", length = 256)
    private String image;

    @Column(name = "update_info", length = 256, nullable = false)
    private String updateInfo;

    @Column(name = "min_memory", nullable = false)
    private Integer minMemory;

    @Column(name = "min_cpu", nullable = false)
    private Integer minCpu;

    @Column(name = "build_cmd", length = 128)
    private String buildCmd;

    @Column(name = "config", length = 4096, nullable = false)
    private String config;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
