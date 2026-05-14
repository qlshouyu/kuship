package cn.kuship.console.modules.appruntime.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * rainbond {@code tenant_service_monitor} —— 组件自定义监控点。
 *
 * <p>真实 schema 共 8 列（无 {@code create_time}），unique_together {@code (name, tenant_id)}
 * 通过业务层校验或 DB 唯一索引；entity 不包含审计列以适配 hibernate {@code validate} 模式。
 */
@Entity
@Table(name = "tenant_service_monitor")
@Getter
@Setter
@NoArgsConstructor
public class ServiceMonitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "service_id", length = 32, nullable = false)
    private String serviceId;

    @Column(name = "path", length = 255, nullable = false)
    private String path;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "service_show_name", length = 64, nullable = false)
    private String serviceShowName;

    @Column(name = "`interval`", length = 10, nullable = false)
    private String interval;
}
