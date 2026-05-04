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

/** rainbond `service_group_relation` —— application(group_id) ↔ component(service_id) 关联。 */
@Entity
@Table(name = "service_group_relation")
@Getter
@Setter
@NoArgsConstructor
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
}
