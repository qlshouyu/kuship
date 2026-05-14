package cn.kuship.console.modules.application.k8sattr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** rainbond `component_k8s_attributes` —— 组件 k8s 属性（nodeSelector / annotations / etc）。 */
@Entity
@Table(name = "component_k8s_attributes")
@Getter
@Setter
@NoArgsConstructor
public class ComponentK8sAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "component_id", length = 32, nullable = false)
    private String componentId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "save_type", length = 32, nullable = false)
    private String saveType;

    @Column(name = "attribute_value", columnDefinition = "longtext", nullable = false)
    private String attributeValue;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        if (updateTime == null) {
            updateTime = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
