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

/** rainbond `service_probe` —— 健康探针（mode=liveness/readiness/startup）。 */
@Entity
@Table(name = "service_probe")
@Getter
@Setter
@NoArgsConstructor
public class ServiceProbe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "service_id", length = 32)
    private String serviceId;

    @Column(name = "probe_id", length = 32)
    private String probeId;

    @Column(name = "mode", length = 20)
    private String mode;

    @Column(name = "scheme", length = 10)
    private String scheme;

    @Column(name = "path", length = 200)
    private String path;

    @Column(name = "port")
    private Integer port;

    @Column(name = "cmd", length = 1024)
    private String cmd;

    @Column(name = "http_header", length = 300)
    private String httpHeader;

    @Column(name = "initial_delay_second")
    private Integer initialDelaySecond;

    @Column(name = "period_second")
    private Integer periodSecond;

    @Column(name = "timeout_second")
    private Integer timeoutSecond;

    @Column(name = "failure_threshold")
    private Integer failureThreshold;

    @Column(name = "success_threshold")
    private Integer successThreshold;

    @Column(name = "is_used")
    private Boolean used;
}
