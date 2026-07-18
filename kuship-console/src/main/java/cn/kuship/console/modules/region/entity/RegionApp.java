package cn.kuship.console.modules.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 映射既有 {@code region_app} 表（region_app_id ↔ console app_id 对照）。只读。
 */
@Entity
@Table(name = "region_app")
public class RegionApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "region_name", length = 64)
    private String regionName;

    @Column(name = "region_app_id", length = 32)
    private String regionAppId;

    @Column(name = "app_id")
    private Integer appId;

    public Integer getId() {
        return id;
    }

    public String getRegionName() {
        return regionName;
    }

    public String getRegionAppId() {
        return regionAppId;
    }

    public Integer getAppId() {
        return appId;
    }
}
