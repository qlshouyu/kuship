package cn.kuship.console.modules.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 映射既有 {@code region_info} 表（Django {@code RegionConfig}，继承 BaseModel）。
 * <p>主键为 BaseModel 的自增 {@code ID}（大写）。供 RegionClient 取地址与凭证。
 */
@Entity
@Table(name = "region_info")
public class RegionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "region_id", length = 36)
    private String regionId;

    @Column(name = "region_name", length = 64)
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
    private String desc;

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

    public Integer getId() {
        return id;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getRegionName() {
        return regionName;
    }

    public String getRegionAlias() {
        return regionAlias;
    }

    public String getUrl() {
        return url;
    }

    public String getWsurl() {
        return wsurl;
    }

    public String getHttpdomain() {
        return httpdomain;
    }

    public String getTcpdomain() {
        return tcpdomain;
    }

    public String getToken() {
        return token;
    }

    public String getStatus() {
        return status;
    }

    public java.time.LocalDateTime getCreateTime() {
        return createTime;
    }

    public String getSslCaCert() {
        return sslCaCert;
    }

    public String getCertFile() {
        return certFile;
    }

    public String getKeyFile() {
        return keyFile;
    }

    public String getEnterpriseId() {
        return enterpriseId;
    }

    public String getProvider() {
        return provider;
    }
}
