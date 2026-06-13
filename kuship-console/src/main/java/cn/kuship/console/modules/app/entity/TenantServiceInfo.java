package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 映射既有 {@code tenant_service}（Django TenantServiceInfo，组件）。只读。
 * {@link #toDict()} 1:1 对齐 BaseModel.to_dict（全列、model 声明顺序、datetime 用空格格式）。
 */
@Entity
@Table(name = "tenant_service")
public class TenantServiceInfo {

    /** to_dict 的 datetime 格式（对齐 BaseModel：'%Y-%m-%d %H:%M:%S'）。 */
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "service_id", length = 32)
    private String serviceId;
    @Column(name = "tenant_id", length = 32)
    private String tenantId;
    @Column(name = "service_key", length = 32)
    private String serviceKey;
    @Column(name = "service_alias", length = 100)
    private String serviceAlias;
    @Column(name = "service_cname", length = 100)
    private String serviceCname;
    @Column(name = "service_region", length = 64)
    private String serviceRegion;
    @Column(name = "`desc`", length = 200)
    private String description;
    @Column(name = "category", length = 15)
    private String category;
    @Column(name = "service_port")
    private Integer servicePort;
    @Column(name = "is_web_service")
    private Boolean isWebService;
    @Column(name = "version", length = 255)
    private String version;
    @Column(name = "update_version")
    private Integer updateVersion;
    @Column(name = "image", length = 200)
    private String image;
    @Column(name = "cmd", length = 2048)
    private String cmd;
    @Column(name = "min_node")
    private Integer minNode;
    @Column(name = "min_cpu")
    private Integer minCpu;
    @Column(name = "container_gpu")
    private Integer containerGpu;
    @Column(name = "min_memory")
    private Integer minMemory;
    @Column(name = "setting", length = 200)
    private String setting;
    @Column(name = "extend_method", length = 32)
    private String extendMethod;
    @Column(name = "env", length = 200)
    private String env;
    @Column(name = "inner_port")
    private Integer innerPort;
    @Column(name = "volume_mount_path", length = 200)
    private String volumeMountPath;
    @Column(name = "host_path", length = 300)
    private String hostPath;
    @Column(name = "deploy_version", length = 20)
    private String deployVersion;
    @Column(name = "code_from", length = 20)
    private String codeFrom;
    @Column(name = "git_url", length = 2047)
    private String gitUrl;
    @Column(name = "create_time")
    private LocalDateTime createTime;
    @Column(name = "git_project_id")
    private Integer gitProjectId;
    @Column(name = "is_code_upload")
    private Boolean isCodeUpload;
    @Column(name = "code_version", length = 100)
    private String codeVersion;
    @Column(name = "service_type", length = 50)
    private String serviceType;
    @Column(name = "creater")
    private Integer creater;
    @Column(name = "language", length = 40)
    private String language;
    @Column(name = "build_strategy", length = 20)
    private String buildStrategy;
    @Column(name = "protocol", length = 15)
    private String protocol;
    @Column(name = "total_memory")
    private Integer totalMemory;
    @Column(name = "is_service")
    private Boolean isService;
    @Column(name = "namespace", length = 100)
    private String namespace;
    @Column(name = "volume_type", length = 64)
    private String volumeType;
    @Column(name = "port_type", length = 15)
    private String portType;
    @Column(name = "service_origin", length = 15)
    private String serviceOrigin;
    @Column(name = "tenant_service_group_id")
    private Integer tenantServiceGroupId;
    @Column(name = "expired_time")
    private LocalDateTime expiredTime;
    @Column(name = "open_webhooks")
    private Boolean openWebhooks;
    @Column(name = "service_source", length = 15)
    private String serviceSource;
    @Column(name = "create_status", length = 15)
    private String createStatus;
    @Column(name = "update_time")
    private LocalDateTime updateTime;
    @Column(name = "check_uuid", length = 36)
    private String checkUuid;
    @Column(name = "check_event_id", length = 32)
    private String checkEventId;
    @Column(name = "docker_cmd", length = 1024)
    private String dockerCmd;
    @Column(name = "secret", length = 64)
    private String secret;
    @Column(name = "server_type", length = 5)
    private String serverType;
    @Column(name = "is_upgrate")
    private Boolean isUpgrate;
    @Column(name = "build_upgrade")
    private Boolean buildUpgrade;
    @Column(name = "service_name", length = 100)
    private String serviceName;
    @Column(name = "oauth_service_id")
    private Integer oauthServiceId;
    @Column(name = "git_full_name", length = 64)
    private String gitFullName;
    @Column(name = "k8s_component_name", length = 100)
    private String k8sComponentName;
    @Column(name = "job_strategy", length = 2047)
    private String jobStrategy;
    @Column(name = "arch", length = 32)
    private String arch;
    @Column(name = "dockerfile", length = 255)
    private String dockerfile;

    /** 1:1 对齐 BaseModel.to_dict：全列、model 声明顺序、datetime→'yyyy-MM-dd HH:mm:ss'。 */
    public Map<String, Object> toDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", id);
        m.put("service_id", serviceId);
        m.put("tenant_id", tenantId);
        m.put("service_key", serviceKey);
        m.put("service_alias", serviceAlias);
        m.put("service_cname", serviceCname);
        m.put("service_region", serviceRegion);
        m.put("desc", description);
        m.put("category", category);
        m.put("service_port", servicePort);
        m.put("is_web_service", isWebService);
        m.put("version", version);
        m.put("update_version", updateVersion);
        m.put("image", image);
        m.put("cmd", cmd);
        m.put("min_node", minNode);
        m.put("min_cpu", minCpu);
        m.put("container_gpu", containerGpu);
        m.put("min_memory", minMemory);
        m.put("setting", setting);
        m.put("extend_method", extendMethod);
        m.put("env", env);
        m.put("inner_port", innerPort);
        m.put("volume_mount_path", volumeMountPath);
        m.put("host_path", hostPath);
        m.put("deploy_version", deployVersion);
        m.put("code_from", codeFrom);
        m.put("git_url", gitUrl);
        m.put("create_time", fmt(createTime));
        m.put("git_project_id", gitProjectId);
        m.put("is_code_upload", isCodeUpload);
        m.put("code_version", codeVersion);
        m.put("service_type", serviceType);
        m.put("creater", creater);
        m.put("language", language);
        m.put("build_strategy", buildStrategy);
        m.put("protocol", protocol);
        m.put("total_memory", totalMemory);
        m.put("is_service", isService);
        m.put("namespace", namespace);
        m.put("volume_type", volumeType);
        m.put("port_type", portType);
        m.put("service_origin", serviceOrigin);
        m.put("tenant_service_group_id", tenantServiceGroupId);
        m.put("expired_time", fmt(expiredTime));
        m.put("open_webhooks", openWebhooks);
        m.put("service_source", serviceSource);
        m.put("create_status", createStatus);
        m.put("update_time", fmt(updateTime));
        m.put("check_uuid", checkUuid);
        m.put("check_event_id", checkEventId);
        m.put("docker_cmd", dockerCmd);
        m.put("secret", secret);
        m.put("server_type", serverType);
        m.put("is_upgrate", isUpgrate);
        m.put("build_upgrade", buildUpgrade);
        m.put("service_name", serviceName);
        m.put("oauth_service_id", oauthServiceId);
        m.put("git_full_name", gitFullName);
        m.put("k8s_component_name", k8sComponentName);
        m.put("job_strategy", jobStrategy);
        m.put("arch", arch);
        m.put("dockerfile", dockerfile);
        return m;
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DT);
    }

    public String getServiceId() { return serviceId; }
    public String getServiceAlias() { return serviceAlias; }
    public String getTenantId() { return tenantId; }
    public String getServiceRegion() { return serviceRegion; }
    public String getCheckUuid() { return checkUuid; }
    public String getExtendMethod() { return extendMethod; }
    public String getK8sComponentName() { return k8sComponentName; }
    public String getServiceSource() { return serviceSource; }
    public String getCreateStatus() { return createStatus; }
}
