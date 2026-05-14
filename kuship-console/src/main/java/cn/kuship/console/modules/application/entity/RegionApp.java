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

/** rainbond {@code region_app} 表 —— console app_id 与 region 端 region_app_id 的映射。 */
@Entity
@Table(name = "region_app")
@Getter
@Setter
@NoArgsConstructor
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
}
