package cn.kuship.console.modules.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 映射既有 {@code tenant_service_group} 表（市场安装应用的 group_key/group_version 记录）。只读。
 */
@Entity
@Table(name = "tenant_service_group")
public class TenantServiceGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "group_name", length = 64)
    private String groupName;

    @Column(name = "group_alias", length = 64)
    private String groupAlias;

    @Column(name = "group_key", length = 32)
    private String groupKey;

    @Column(name = "group_version", length = 32)
    private String groupVersion;

    @Column(name = "region_name", length = 64)
    private String regionName;

    @Column(name = "service_group_id")
    private Integer serviceGroupId;

    public Integer getId() {
        return id;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public String getGroupVersion() {
        return groupVersion;
    }

    public Integer getServiceGroupId() {
        return serviceGroupId;
    }
}
