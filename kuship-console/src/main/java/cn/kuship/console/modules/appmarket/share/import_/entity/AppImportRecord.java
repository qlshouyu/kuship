package cn.kuship.console.modules.appmarket.share.import_.entity;

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

/** rainbond `app_import_record` —— 应用模板导入事件追踪。 */
@Entity
@Table(name = "app_import_record")
@Getter
@Setter
@NoArgsConstructor
public class AppImportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "event_id", length = 32)
    private String eventId;

    @Column(name = "status", length = 15)
    private String status;

    @Column(name = "scope", length = 10)
    private String scope;

    @Column(name = "format", length = 15)
    private String format;

    @Column(name = "source_dir", length = 256)
    private String sourceDir;

    @Column(name = "team_name", length = 64)
    private String teamName;

    @Column(name = "region", length = 64)
    private String region;

    @Column(name = "user_name", length = 64)
    private String userName;

    @Column(name = "enterprise_id", length = 64)
    private String enterpriseId;

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
