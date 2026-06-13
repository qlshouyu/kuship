package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 映射既有 {@code service_labels}（组件↔标签）。只读。 */
@Entity
@Table(name = "service_labels")
public class ServiceLabels {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "ID")
    private Integer id;
    @Column(name = "tenant_id", length = 32) private String tenantId;
    @Column(name = "service_id", length = 32) private String serviceId;
    @Column(name = "label_id", length = 32) private String labelId;
    @Column(name = "region", length = 30) private String region;
    @Column(name = "create_time") private java.time.LocalDateTime createTime;
    public String getLabelId() { return labelId; }
}
