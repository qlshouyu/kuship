package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/** 映射既有 {@code tenant_service_volume}（组件持久化）。只读 + to_dict(model 顺序)。 */
@Entity
@Table(name = "tenant_service_volume")
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

    /** BaseModel.to_dict（model 声明顺序）。allow_expansion→bool、mode→int(可空)。 */
    public Map<String, Object> toDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", id);
        m.put("service_id", serviceId);
        m.put("category", category);
        m.put("host_path", hostPath);
        m.put("volume_type", volumeType);
        m.put("volume_path", volumePath);
        m.put("volume_name", volumeName);
        m.put("volume_capacity", volumeCapacity);
        m.put("volume_provider_name", volumeProviderName);
        m.put("access_mode", accessMode);
        m.put("share_policy", sharePolicy);
        m.put("backup_policy", backupPolicy);
        m.put("reclaim_policy", reclaimPolicy);
        m.put("allow_expansion", allowExpansion);
        m.put("mode", mode);
        return m;
    }

    public String getVolumeName() { return volumeName; }
}
