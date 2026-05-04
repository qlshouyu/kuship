package cn.kuship.console.modules.application.entity;

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

/** rainbond `service_group` 表 —— 应用（application）。 */
@Entity
@Table(name = "service_group")
@Getter
@Setter
@NoArgsConstructor
public class ServiceGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "group_name", length = 128)
    private String groupName;

    @Column(name = "region_name", length = 64)
    private String regionName;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "note", length = 2048)
    private String note;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "governance_mode", length = 255)
    private String governanceMode;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "app_type", length = 255)
    private String appType;

    @Column(name = "app_store_name", length = 255)
    private String appStoreName;

    @Column(name = "app_store_url", length = 255)
    private String appStoreUrl;

    @Column(name = "app_template_name", length = 255)
    private String appTemplateName;

    @Column(name = "version", length = 255)
    private String version;

    @Column(name = "logo", length = 255)
    private String logo;

    @Column(name = "k8s_app", length = 64)
    private String k8sApp;
}
