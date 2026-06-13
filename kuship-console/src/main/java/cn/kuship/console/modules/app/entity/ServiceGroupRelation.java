package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 映射既有 {@code service_group_relation}（组件↔应用组关系）。只读。 */
@Entity
@Table(name = "service_group_relation")
public class ServiceGroupRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "service_id", length = 32)
    private String serviceId;

    @Column(name = "group_id")
    private Integer groupId;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "region_name", length = 64)
    private String regionName;

    public Integer getGroupId() { return groupId; }
    public String getServiceId() { return serviceId; }
}
