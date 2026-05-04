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

/**
 * rainbond `tenant_perms` 表 —— 用户-团队-角色 关联表（同时关联 enterprise）。
 *
 * <p>该表是 rainbond 团队成员体系的核心：每行表示一个用户在某个 team 中的成员关系。
 * 字段 {@code identity} 是历史遗留（owner/admin/developer/viewer/access），新版 RBAC 走 {@code roleId} 关联到 role_info。
 */
@Entity
@Table(name = "tenant_perms")
@Getter
@Setter
@NoArgsConstructor
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
