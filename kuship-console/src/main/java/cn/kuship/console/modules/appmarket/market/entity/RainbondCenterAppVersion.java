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

/** rainbond `rainbond_center_app_version` —— 应用模板版本（25 列，含 longtext app_template）。 */
@Entity
@Table(name = "rainbond_center_app_version")
@Getter
@Setter
@NoArgsConstructor
public class RainbondCenterAppVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "enterprise_id", length = 32, nullable = false)
    private String enterpriseId;

    @Column(name = "app_id", length = 32, nullable = false)
    private String appId;

    @Column(name = "version", length = 32, nullable = false)
    private String version;

    @Column(name = "version_alias", length = 64, nullable = false)
    private String versionAlias;

    @Column(name = "app_version_info", length = 255, nullable = false)
    private String appVersionInfo;

    @Column(name = "record_id", nullable = false)
    private Integer recordId;

    @Column(name = "share_user", nullable = false)
    private Integer shareUser;

    @Column(name = "share_team", length = 64, nullable = false)
    private String shareTeam;

    @Column(name = "group_id", nullable = false)
    private Integer groupId;

    @Column(name = "dev_status", length = 32)
    private String devStatus;

    @Column(name = "source", length = 15)
    private String source;

    @Column(name = "scope", length = 15)
    private String scope;

    @Column(name = "app_template", columnDefinition = "longtext", nullable = false)
    private String appTemplate;

    @Column(name = "template_version", length = 10, nullable = false)
    private String templateVersion;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "upgrade_time", length = 30, nullable = false)
    private String upgradeTime;

    @Column(name = "install_number", nullable = false)
    private Integer installNumber;

    @Column(name = "is_official", nullable = false)
    private Boolean isOfficial;

    @Column(name = "is_ingerit", nullable = false)
    private Boolean isIngerit;

    @Column(name = "is_complete", nullable = false)
    private Boolean isComplete;

    @Column(name = "template_type", length = 32)
    private String templateType;

    @Column(name = "release_user_id")
    private Integer releaseUserId;

    @Column(name = "region_name", length = 64)
    private String regionName;

    @Column(name = "is_plugin", nullable = false)
    private Boolean isPlugin;

    @Column(name = "arch", length = 32, nullable = false)
    private String arch;
}
