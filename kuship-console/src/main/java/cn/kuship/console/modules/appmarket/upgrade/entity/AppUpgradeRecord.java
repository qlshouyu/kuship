package cn.kuship.console.modules.appmarket.upgrade.entity;

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

/** rainbond `app_upgrade_record` —— 整组应用升级记录（17 列）。 */
@Entity
@Table(name = "app_upgrade_record")
@Getter
@Setter
@NoArgsConstructor
public class AppUpgradeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 33, nullable = false)
    private String tenantId;

    @Column(name = "group_id", nullable = false)
    private Integer groupId;

    @Column(name = "group_key", length = 32, nullable = false)
    private String groupKey;

    @Column(name = "group_name", length = 64, nullable = false)
    private String groupName;

    @Column(name = "version", length = 20, nullable = false)
    private String version;

    @Column(name = "old_version", length = 20, nullable = false)
    private String oldVersion;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "market_name", length = 64)
    private String marketName;

    @Column(name = "is_from_cloud", nullable = false)
    private Boolean isFromCloud;

    @Column(name = "upgrade_group_id", nullable = false)
    private Integer upgradeGroupId;

    @Column(name = "snapshot_id", length = 32)
    private String snapshotId;

    @Column(name = "record_type", length = 64)
    private String recordType;

    @Column(name = "parent_id", nullable = false)
    private Integer parentId;
}
