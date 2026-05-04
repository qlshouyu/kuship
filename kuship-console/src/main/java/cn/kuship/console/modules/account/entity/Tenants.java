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

import java.time.LocalDateTime;

/** rainbond `tenant_info` 表（Tenants/Team）。注意 {@code creater} 是 rainbond 历史拼写。 */
@Entity
@Table(name = "tenant_info")
@Getter
@Setter
@NoArgsConstructor
public class Tenants {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 33, unique = true)
    private String tenantId;

    @Column(name = "tenant_name", length = 64, unique = true)
    private String tenantName;

    @Column(name = "is_active")
    private Boolean active;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    /** rainbond 字段名 typo —— 保留 `creater`，不要重命名为 `creator`。 */
    @Column(name = "creater")
    private Integer creater;

    @Column(name = "limit_memory")
    private Integer limitMemory;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "tenant_alias", length = 64)
    private String tenantAlias;

    @Column(name = "enterprise_id", length = 32)
    private String enterpriseId;

    @Column(name = "namespace", length = 33, unique = true)
    private String namespace;

    @Column(name = "logo", length = 2048)
    private String logo;
}
