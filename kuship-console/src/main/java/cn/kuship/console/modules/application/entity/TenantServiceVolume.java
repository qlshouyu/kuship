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

/** rainbond `tenant_service_volume` —— 组件存储卷。 */
@Entity
@Table(name = "tenant_service_volume")
@Getter
@Setter
@NoArgsConstructor
public class TenantServiceVolume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "service_id", length = 32)
    private String serviceId;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "host_path", length = 400)
    private String hostPath;

    @Column(name = "volume_type", length = 64)
    private String volumeType;

    @Column(name = "volume_path", length = 400)
    private String volumePath;

    @Column(name = "volume_name", length = 100)
    private String volumeName;

    @Column(name = "volume_capacity")
    private Integer volumeCapacity;

    @Column(name = "volume_provider_name", length = 100)
    private String volumeProviderName;

    @Column(name = "access_mode", length = 100)
    private String accessMode;

    @Column(name = "share_policy", length = 100)
    private String sharePolicy;

    @Column(name = "backup_policy", length = 100)
    private String backupPolicy;

    @Column(name = "reclaim_policy", length = 100)
    private String reclaimPolicy;

    @Column(name = "allow_expansion")
    private Boolean allowExpansion;

    @Column(name = "mode")
    private Integer mode;
}
