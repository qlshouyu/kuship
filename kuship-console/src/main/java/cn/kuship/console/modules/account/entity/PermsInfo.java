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

/** rainbond `perms_info` 表 —— 权限码元数据。code 是权限码常量（如 `app_create=300101`）。 */
@Entity
@Table(name = "perms_info")
@Getter
@Setter
@NoArgsConstructor
public class PermsInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "name", length = 128, unique = true)
    private String name;

    @Column(name = "`desc`", length = 128)
    private String description;

    @Column(name = "code", unique = true)
    private Integer code;

    @Column(name = "`group`", length = 128)
    private String group;

    @Column(name = "kind", length = 128)
    private String kind;
}
