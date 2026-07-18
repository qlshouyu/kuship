package cn.kuship.console.modules.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 映射既有 {@code app_market} 表（Django {@code AppMarket}）。
 * <p>只读：平台插件列表用其 url/access_key 组装云端市场请求（对齐 _build_platform_market）。
 */
@Entity
@Table(name = "app_market")
public class AppMarket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "name", length = 64)
    private String name;

    @Column(name = "url", length = 255)
    private String url;

    @Column(name = "domain", length = 64)
    private String domain;

    @Column(name = "access_key", length = 255)
    private String accessKey;

    @Column(name = "enterprise_id", length = 64)
    private String enterpriseId;

    @Column(name = "type", length = 32)
    private String type;

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getDomain() {
        return domain;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getEnterpriseId() {
        return enterpriseId;
    }

    public String getType() {
        return type;
    }
}
