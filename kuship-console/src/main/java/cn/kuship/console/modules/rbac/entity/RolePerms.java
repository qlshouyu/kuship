package cn.kuship.console.modules.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 映射既有 {@code role_perms}（角色↔权限码关联，PK 大写 ID；表名带 s）。
 * <p>{@code role_id}/{@code perm_code}/{@code app_id} 均为 int；{@code app_id=-1} 表示全局（非应用级）权限。
 */
@Getter
@Setter
@Entity
@Table(name = "role_perms")
public class RolePerms {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "perm_code")
    private Integer permCode;

    @Column(name = "app_id")
    private Integer appId;
}
