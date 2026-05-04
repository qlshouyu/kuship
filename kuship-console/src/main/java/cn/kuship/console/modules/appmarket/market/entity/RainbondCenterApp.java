package cn.kuship.console.modules.appmarket.market.entity;

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

/** rainbond `rainbond_center_app` —— 应用模板。19 列，注意 `is_ingerit` 历史拼写错。 */
@Entity
@Table(name = "rainbond_center_app")
@Getter
@Setter
@NoArgsConstructor
public class RainbondCenterApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "app_id", length = 32, nullable = false)
    private String appId;

    @Column(name = "app_name", length = 64, nullable = false)
    private String appName;

    @Column(name = "create_user")
    private Integer createUser;

    @Column(name = "create_team", length = 64)
    private String createTeam;

    @Column(name = "pic", length = 200)
    private String pic;

    @Column(name = "source", length = 128)
    private String source;

    @Column(name = "dev_status", length = 32)
    private String devStatus;

    @Column(name = "scope", length = 50, nullable = false)
    private String scope;

    @Column(name = "`describe`", length = 400)
    private String describe;

    @Column(name = "is_ingerit", nullable = false)
    private Boolean isIngerit;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "enterprise_id", length = 32, nullable = false)
    private String enterpriseId;

    @Column(name = "install_number", nullable = false)
    private Integer installNumber;

    @Column(name = "is_official", nullable = false)
    private Boolean isOfficial;

    @Column(name = "details", columnDefinition = "longtext")
    private String details;

    @Column(name = "arch", length = 32, nullable = false)
    private String arch;

    @Column(name = "is_version", nullable = false)
    private Boolean isVersion;
}
