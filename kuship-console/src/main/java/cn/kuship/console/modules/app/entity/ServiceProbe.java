package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/** 映射既有 {@code service_probe}（组件探针）。只读 + to_dict(model 顺序)。 */
@Entity
@Table(name = "service_probe")
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
    private Boolean isUsed;

    public Map<String, Object> toDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", id);
        m.put("service_id", serviceId);
        m.put("probe_id", probeId);
        m.put("mode", mode);
        m.put("scheme", scheme);
        m.put("path", path);
        m.put("port", port);
        m.put("cmd", cmd);
        m.put("http_header", httpHeader);
        m.put("initial_delay_second", initialDelaySecond);
        m.put("period_second", periodSecond);
        m.put("timeout_second", timeoutSecond);
        m.put("failure_threshold", failureThreshold);
        m.put("success_threshold", successThreshold);
        m.put("is_used", isUsed);
        return m;
    }
}
