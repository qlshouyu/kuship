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

/** rainbond `tenant_service_plugin_relation` —— 组件 → 插件版本 + 启停状态。 */
@Entity
@Table(name = "tenant_service_plugin_relation")
@Getter
@Setter
@NoArgsConstructor
public class TenantServicePluginRelation {

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

    @Column(name = "plugin_status", nullable = false)
    private Boolean pluginStatus;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "min_memory", nullable = false)
    private Integer minMemory;

    @Column(name = "min_cpu")
    private Integer minCpu;
}
