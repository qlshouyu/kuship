package cn.kuship.console.modules.application.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** rainbond `service_labels` —— 组件 node label 关联（service_id ↔ region 端 label_id）。 */
@Entity
@Table(name = "service_labels")
@Getter
@Setter
@NoArgsConstructor
public class TenantServiceLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "service_id", length = 32, nullable = false)
    private String serviceId;

    @Column(name = "label_id", length = 32, nullable = false)
    private String labelId;

    @Column(name = "region", length = 30, nullable = false)
    private String region;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
