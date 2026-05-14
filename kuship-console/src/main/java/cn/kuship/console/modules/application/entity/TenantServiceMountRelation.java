package cn.kuship.console.modules.application.entity;

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
 * rainbond {@code tenant_service_mnt_relation} —— 组件挂载依赖存储关系。
 *
 * <p>Python 模型：{@code www/models/main.py::TenantServiceMountRelation}
 * 唯一约束：{@code (service_id, dep_service_id, mnt_name)}
 */
@Entity
@Table(name = "tenant_service_mnt_relation")
@Getter
@Setter
@NoArgsConstructor
public class TenantServiceMountRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    /** 租户 ID（char 32）。 */
    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    /** 发起挂载的组件 service_id（char 32）。 */
    @Column(name = "service_id", length = 32)
    private String serviceId;

    /** 被挂载（依赖）的组件 service_id（char 32）。 */
    @Column(name = "dep_service_id", length = 32)
    private String depServiceId;

    /** 被挂载的存储卷名称（mnt_name = volume_name）。 */
    @Column(name = "mnt_name", length = 100)
    private String mntName;

    /** 本地挂载路径。 */
    @Column(name = "mnt_dir", length = 400)
    private String mntDir;
}
