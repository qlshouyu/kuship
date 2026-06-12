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

/** 映射既有 {@code tenant_region}（团队↔集群关系，PK 大写 ID）。 */
@Getter
@Setter
@Entity
@Table(name = "tenant_region")
public class TenantRegionInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 33)
    private String tenantId;

    @Column(name = "region_name", length = 64)
    private String regionName;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_init")
    private Boolean isInit;

    @Column(name = "service_status")
    private Integer serviceStatus;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "region_tenant_name", length = 64)
    private String regionTenantName;

    @Column(name = "region_tenant_id", length = 32)
    private String regionTenantId;

    @Column(name = "region_scope", length = 32)
    private String regionScope;
}
