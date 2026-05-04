package cn.kuship.console.modules.account.entity;

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

/** rainbond `tenant_region` 表 —— 团队-集群关联。tenant_id 形如 char(33) 与 region_name 联合唯一。 */
@Entity
@Table(name = "tenant_region")
@Getter
@Setter
@NoArgsConstructor
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
    private Boolean active;

    @Column(name = "is_init")
    private Boolean init;

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

    @Column(name = "enterprise_id", length = 32)
    private String enterpriseId;
}
