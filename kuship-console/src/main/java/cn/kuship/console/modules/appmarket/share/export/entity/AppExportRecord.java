package cn.kuship.console.modules.appmarket.share.export.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** rainbond `app_export_record` —— 应用模板导出事件追踪。 */
@Entity
@Table(name = "app_export_record")
@Getter
@Setter
@NoArgsConstructor
public class AppExportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "group_key", length = 32, nullable = false)
    private String groupKey;

    @Column(name = "version", length = 20, nullable = false)
    private String version;

    @Column(name = "format", length = 15, nullable = false)
    private String format;

    @Column(name = "event_id", length = 32)
    private String eventId;

    @Column(name = "status", length = 10)
    private String status;

    @Column(name = "file_path", length = 256)
    private String filePath;

    @Column(name = "enterprise_id", length = 32, nullable = false)
    private String enterpriseId;

    @Column(name = "region_name", length = 32)
    private String regionName;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) createTime = now;
        if (updateTime == null) updateTime = now;
    }

    @PreUpdate
    void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
