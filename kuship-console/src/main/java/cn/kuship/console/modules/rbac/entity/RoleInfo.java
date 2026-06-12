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
 * 映射既有 {@code role_info}（角色定义，PK 大写 ID）。
 * <p>{@code kind} 区分角色范围（如 {@code team}），{@code kind_id} 存该范围的标识（团队为 tenant_id，非 PK）。
 */
@Getter
@Setter
@Entity
@Table(name = "role_info")
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
