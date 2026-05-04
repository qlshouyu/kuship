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

import java.time.LocalDateTime;

/**
 * rainbond `tenant_service` —— 组件（component）。50+ 列大表。
 *
 * <p>本 entity 一次性映射全部列；application-core change 不维护 tenant_service_group_id 字段（用 service_group_relation 关联）。
 */
@Entity
@Table(name = "tenant_service")
@Getter
@Setter
@NoArgsConstructor
public class TenantService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "service_id", length = 32, unique = true)
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
    private Boolean webService;

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
    private Boolean codeUpload;

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
    private Boolean upgrate;

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
}
