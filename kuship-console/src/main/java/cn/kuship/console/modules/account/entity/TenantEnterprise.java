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

/** rainbond `tenant_enterprise` 表。{@code isActive} 是 IntegerField (0=未激活,1=已激活)。 */
@Entity
@Table(name = "tenant_enterprise")
@Getter
@Setter
@NoArgsConstructor
public class TenantEnterprise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "enterprise_id", length = 32, unique = true)
    private String enterpriseId;

    @Column(name = "enterprise_name", length = 64)
    private String enterpriseName;

    @Column(name = "enterprise_alias", length = 64)
    private String enterpriseAlias;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "enterprise_token", length = 256)
    private String enterpriseToken;

    @Column(name = "is_active")
    private Integer isActive;

    @Column(name = "logo", length = 128)
    private String logo;

    @Column(name = "enable_team_resource_view")
    private Boolean enableTeamResourceView;
}
