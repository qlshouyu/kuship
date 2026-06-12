package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 映射既有 {@code service_group}（组件分组=应用，PK 大写 ID）。
 * <p>本轮只读、只映射应用列表所需列；未映射列（is_default/governance_mode 等）不影响 validate。
 */
@Getter
@Setter
@Entity
@Table(name = "service_group")
public class ServiceGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "group_name", length = 128)
    private String groupName;

    @Column(name = "region_name", length = 64)
    private String regionName;

    @Column(name = "note", length = 2048)
    private String note;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
