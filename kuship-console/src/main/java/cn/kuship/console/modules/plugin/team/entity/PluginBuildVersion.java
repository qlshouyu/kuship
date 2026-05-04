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

/** rainbond `plugin_build_version` —— 插件构建版本（双状态字段）。 */
@Entity
@Table(name = "plugin_build_version")
@Getter
@Setter
@NoArgsConstructor
public class PluginBuildVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "plugin_id", length = 32, nullable = false)
    private String pluginId;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "region", length = 64, nullable = false)
    private String region;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "update_info", length = 256, nullable = false)
    private String updateInfo;

    @Column(name = "build_version", length = 32, nullable = false)
    private String buildVersion;

    @Column(name = "build_status", length = 32, nullable = false)
    private String buildStatus;

    @Column(name = "plugin_version_status", length = 32, nullable = false)
    private String pluginVersionStatus;

    @Column(name = "min_memory", nullable = false)
    private Integer minMemory;

    @Column(name = "min_cpu", nullable = false)
    private Integer minCpu;

    @Column(name = "event_id", length = 32)
    private String eventId;

    @Column(name = "build_cmd", length = 128)
    private String buildCmd;

    @Column(name = "image_tag", length = 100)
    private String imageTag;

    @Column(name = "code_version", length = 32)
    private String codeVersion;

    @Column(name = "build_time", nullable = false)
    private LocalDateTime buildTime;
}
