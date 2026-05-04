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

/** rainbond `tenant_permission_group` 表 —— 权限分组（业务上不再使用，但表仍在 schema 中）。 */
@Entity
@Table(name = "tenant_permission_group")
@Getter
@Setter
@NoArgsConstructor
public class PermGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "group_name", length = 64)
    private String groupName;
}
