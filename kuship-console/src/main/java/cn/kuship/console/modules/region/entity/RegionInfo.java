package cn.kuship.console.modules.region.entity;

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
 * rainbond `region_info` 表 —— 集群信息。
 *
 * <p>本 entity 用于业务层 CRUD（{@code modules/region}）；infrastructure 层的 mTLS RestClient 装配
 * 仍走 {@code RegionInfoRepository}（JdbcTemplate）+ {@code RegionInfoDto}，不进入 hibernate session。
 *
 * <p>注意：{@code desc} 是 SQL 保留字，必须用反引号；{@code @Column(name="`desc`")}。
 */
@Entity
@Table(name = "region_info")
@Getter
@Setter
@NoArgsConstructor
public class RegionInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "region_id", length = 36, unique = true)
    private String regionId;

    @Column(name = "region_name", length = 64, unique = true)
    private String regionName;

    @Column(name = "region_alias", length = 64)
    private String regionAlias;

    @Column(name = "region_type", length = 64)
    private String regionType;

    @Column(name = "url", length = 256)
    private String url;

    @Column(name = "wsurl", length = 256)
    private String wsurl;

    @Column(name = "httpdomain", length = 256)
    private String httpdomain;

    @Column(name = "tcpdomain", length = 256)
    private String tcpdomain;

    @Column(name = "token", length = 255)
    private String token;

    @Column(name = "status", length = 2)
    private String status;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "`desc`", length = 200)
    private String description;

    @Column(name = "scope", length = 10)
    private String scope;

    @Column(name = "ssl_ca_cert")
    private String sslCaCert;

    @Column(name = "cert_file")
    private String certFile;

    @Column(name = "key_file")
    private String keyFile;

    @Column(name = "enterprise_id", length = 36)
    private String enterpriseId;

    @Column(name = "provider", length = 24)
    private String provider;

    @Column(name = "provider_cluster_id", length = 64)
    private String providerClusterId;
}
