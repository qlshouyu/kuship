package cn.kuship.console.modules.misc.webhook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** rainbond `service_webhooks` —— Webhook 配置（5 列）。 */
@Entity
@Table(name = "service_webhooks")
@Getter
@Setter
@NoArgsConstructor
public class ServiceWebhooks {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "service_id", length = 32, nullable = false)
    private String serviceId;

    @Column(name = "state", nullable = false)
    private Boolean state;

    @Column(name = "webhooks_type", length = 128, nullable = false)
    private String webhooksType;

    @Column(name = "deploy_keyword", length = 128, nullable = false)
    private String deployKeyword;

    @Column(name = "trigger", length = 256, nullable = false)
    private String trigger;
}
