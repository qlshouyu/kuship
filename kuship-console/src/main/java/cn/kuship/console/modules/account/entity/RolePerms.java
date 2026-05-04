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

/** rainbond `role_perms` 表 —— 角色-权限码 关联。app_id 默认 -1 表示团队级权限（非应用级）。 */
@Entity
@Table(name = "role_perms")
@Getter
@Setter
@NoArgsConstructor
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
