package cn.kuship.console.modules.appmarket.share.entity;

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

/** rainbond `service_share_record` —— 服务分享记录（19 列，6-step / 3-status 状态机）。 */
@Entity
@Table(name = "service_share_record")
@Getter
@Setter
@NoArgsConstructor
public class ServiceShareRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "group_share_id", length = 32, nullable = false, unique = true)
    private String groupShareId;

    @Column(name = "group_id", length = 32, nullable = false)
    private String groupId;

    @Column(name = "team_name", length = 64, nullable = false)
    private String teamName;

    @Column(name = "event_id", length = 32)
    private String eventId;

    @Column(name = "share_version", length = 15)
    private String shareVersion;

    @Column(name = "share_version_alias", length = 64)
    private String shareVersionAlias;

    @Column(name = "share_app_version_info", length = 255, nullable = false)
    private String shareAppVersionInfo;

    @Column(name = "is_success", nullable = false)
    private Boolean isSuccess;

    @Column(name = "step", nullable = false)
    private Integer step;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "app_id", length = 64)
    private String appId;

    @Column(name = "scope", length = 64)
    private String scope;

    @Column(name = "share_app_market_name", length = 64)
    private String shareAppMarketName;

    @Column(name = "share_store_name", length = 64)
    private String shareStoreName;

    @Column(name = "share_app_model_name", length = 64)
    private String shareAppModelName;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
