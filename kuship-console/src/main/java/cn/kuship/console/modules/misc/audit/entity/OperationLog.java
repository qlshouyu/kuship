package cn.kuship.console.modules.misc.audit.entity;

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

/** rainbond `operation_log` —— 操作审计日志（14 列含 longtext old/new_information）。 */
@Entity
@Table(name = "operation_log")
@Getter
@Setter
@NoArgsConstructor
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "operation_type", length = 32, nullable = false)
    private String operationType;

    @Column(name = "enterprise_id", length = 32, nullable = false)
    private String enterpriseId;

    @Column(name = "team_name", length = 32, nullable = false)
    private String teamName;

    @Column(name = "app_id", nullable = false)
    private Integer appId;

    @Column(name = "service_alias", length = 32, nullable = false)
    private String serviceAlias;

    @Column(name = "comment", columnDefinition = "longtext", nullable = false)
    private String comment;

    @Column(name = "is_openapi", nullable = false)
    private Boolean isOpenapi;

    @Column(name = "service_cname", length = 100, nullable = false)
    private String serviceCname;

    @Column(name = "app_name", length = 128, nullable = false)
    private String appName;

    @Column(name = "old_information", columnDefinition = "longtext")
    private String oldInformation;

    @Column(name = "new_information", columnDefinition = "longtext")
    private String newInformation;

    @Column(name = "information_type", length = 32, nullable = false)
    private String informationType;
}
