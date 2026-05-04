package cn.kuship.console.modules.appmarket.backup.entity;

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

/** rainbond `groupapp_backup` —— 整组应用备份元数据。 */
@Entity
@Table(name = "groupapp_backup")
@Getter
@Setter
@NoArgsConstructor
public class ServiceGroupBackup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "group_id", nullable = false)
    private Integer groupId;

    @Column(name = "event_id", length = 32)
    private String eventId;

    @Column(name = "group_uuid", length = 32)
    private String groupUuid;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "backup_id", length = 36)
    private String backupId;

    @Column(name = "team_id", length = 32)
    private String teamId;

    @Column(name = "user", length = 64)
    private String user;

    @Column(name = "region", length = 64)
    private String region;

    @Column(name = "status", length = 15)
    private String status;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "mode", length = 15)
    private String mode;

    @Column(name = "source_dir", length = 256)
    private String sourceDir;

    @Column(name = "backup_size", nullable = false)
    private Long backupSize;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "total_memory", nullable = false)
    private Integer totalMemory;

    @Column(name = "backup_server_info", length = 400)
    private String backupServerInfo;

    @Column(name = "source_type", length = 32)
    private String sourceType;
}
