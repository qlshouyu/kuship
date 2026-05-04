package cn.kuship.console.modules.appcreate.entity;

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

/**
 * rainbond `service_source` 表 —— 组件创建参数（git/image/build_strategy 等独立留底）。
 */
@Entity
@Table(name = "service_source")
@Getter
@Setter
@NoArgsConstructor
public class ServiceSourceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "team_id", length = 32)
    private String teamId;

    @Column(name = "user_name", length = 255)
    private String userName;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "group_key", length = 32)
    private String groupKey;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "service_share_uuid", length = 65)
    private String serviceShareUuid;

    @Column(name = "extend_info", length = 1024)
    private String extendInfo;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "service_id", length = 32, unique = true)
    private String serviceId;
}
