package cn.kuship.console.modules.plugin.team.entity;

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

/** rainbond `plugin_share_record_event` —— 插件分享流程事件子表。 */
@Entity
@Table(name = "plugin_share_record_event")
@Getter
@Setter
@NoArgsConstructor
public class PluginShareRecordEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "record_id", nullable = false)
    private Integer recordId;

    @Column(name = "region_share_id", length = 36, nullable = false)
    private String regionShareId;

    @Column(name = "team_id", length = 32, nullable = false)
    private String teamId;

    @Column(name = "team_name", length = 64, nullable = false)
    private String teamName;

    @Column(name = "plugin_id", length = 32, nullable = false)
    private String pluginId;

    @Column(name = "plugin_name", length = 64, nullable = false)
    private String pluginName;

    @Column(name = "event_id", length = 32, nullable = false)
    private String eventId;

    @Column(name = "event_status", length = 32, nullable = false)
    private String eventStatus;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
