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

/** rainbond `role_info` 表。kind 通常是 `team` / `enterprise`，kind_id 是对应的 tenantId/enterpriseId。 */
@Entity
@Table(name = "role_info")
@Getter
@Setter
@NoArgsConstructor
public class RoleInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "name", length = 32)
    private String name;

    @Column(name = "kind_id", length = 64)
    private String kindId;

    @Column(name = "kind", length = 32)
    private String kind;
}
