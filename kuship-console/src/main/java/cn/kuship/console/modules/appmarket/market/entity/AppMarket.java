package cn.kuship.console.modules.appmarket.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** rainbond `app_market` —— 远程应用市场凭据（与本地 app-models 对应的远程源）。 */
@Entity
@Table(name = "app_market")
@Getter
@Setter
@NoArgsConstructor
public class AppMarket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "url", length = 255, nullable = false)
    private String url;

    @Column(name = "domain", length = 64, nullable = false)
    private String domain;

    @Column(name = "access_key", length = 255)
    private String accessKey;

    @Column(name = "enterprise_id", length = 32, nullable = false)
    private String enterpriseId;

    @Column(name = "type", length = 32, nullable = false)
    private String type;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "is_personal", nullable = false)
    private Boolean isPersonal;
}
