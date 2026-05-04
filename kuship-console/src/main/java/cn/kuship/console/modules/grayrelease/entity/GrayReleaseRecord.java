package cn.kuship.console.modules.grayrelease.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** rainbond `gray_release_record` 表 —— 应用级灰度发布记录。schema 由 rainbond migration 0002 创建。 */
@Entity
@Table(name = "gray_release_record")
@Getter
@Setter
@NoArgsConstructor
public class GrayReleaseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "region_name", length = 64, nullable = false)
    private String regionName;

    @Column(name = "app_id", nullable = false)
    private Integer appId;

    @Column(name = "app_name", length = 128)
    private String appName;

    @Column(name = "template_id", length = 128)
    private String templateId;

    @Column(name = "template_name", length = 255)
    private String templateName;

    @Column(name = "template_version", length = 64)
    private String templateVersion;

    @Column(name = "original_upgrade_group_id")
    private Integer originalUpgradeGroupId;

    @Column(name = "gray_upgrade_group_id")
    private Integer grayUpgradeGroupId;

    @Column(name = "original_service_id", length = 32)
    private String originalServiceId;

    @Column(name = "original_service_cname", length = 100)
    private String originalServiceCname;

    @Column(name = "gray_service_id", length = 32)
    private String grayServiceId;

    @Column(name = "gray_service_cname", length = 100)
    private String grayServiceCname;

    @Convert(converter = ServiceMappingsConverter.class)
    @Column(name = "service_mappings", columnDefinition = "TEXT")
    private List<ServiceMappingEntry> serviceMappings;

    @Column(name = "domain_name", length = 255)
    private String domainName;

    @Column(name = "gray_ratio", nullable = false)
    private Integer grayRatio;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private GrayReleaseStatus status;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
