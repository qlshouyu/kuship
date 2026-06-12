package cn.kuship.console.modules.team.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 映射既有 {@code tenant_info}（Django Tenants，团队/租户，PK 大写 ID）。 */
@Getter
@Setter
@Entity
@Table(name = "tenant_info")
public class Tenants {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 33)
    private String tenantId;

    @Column(name = "tenant_name", length = 64)
    private String tenantName;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "creater")
    private Integer creater;

    @Column(name = "limit_memory")
    private Integer limitMemory;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "tenant_alias", length = 64)
    private String tenantAlias;

    @Column(name = "enterprise_id", length = 32)
    private String enterpriseId;

    @Column(name = "namespace", length = 33)
    private String namespace;

    @Column(name = "logo", length = 2048)
    private String logo;
}
