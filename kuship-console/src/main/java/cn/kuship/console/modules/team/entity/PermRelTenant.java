package cn.kuship.console.modules.team.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 映射既有 {@code tenant_perms}（用户↔团队 成员关系，PK 大写 ID）。
 * 注意：{@code tenant_id}/{@code enterprise_id} 在该表是整型（指向 tenant_info.ID / 企业整型 ID）。
 */
@Getter
@Setter
@Entity
@Table(name = "tenant_perms")
public class PermRelTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "tenant_id")
    private Integer tenantId;

    @Column(name = "identity", length = 15)
    private String identity;

    @Column(name = "enterprise_id")
    private Integer enterpriseId;

    @Column(name = "role_id")
    private Integer roleId;
}
