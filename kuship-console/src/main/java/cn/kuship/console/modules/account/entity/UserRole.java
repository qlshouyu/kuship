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
 * rainbond `user_role` 表 —— 用户-角色 关联（注意 user_id / role_id 在 schema 中是 char(32)）。
 *
 * <p>与 {@link PermRelTenant} 的区别：tenant_perms 在 rainbond 中用于"我属于哪个 team"；user_role 用于"我在
 * 这个 team 中拥有哪些角色"。本字段类型保留 String 与 rainbond 一致。
 */
@Entity
@Table(name = "user_role")
@Getter
@Setter
@NoArgsConstructor
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "user_id", length = 32)
    private String userId;

    @Column(name = "role_id", length = 32)
    private String roleId;
}
