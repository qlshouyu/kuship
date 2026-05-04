package cn.kuship.console.modules.plugin.team.entity;

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

/** rainbond `tenant_plugin` —— 团队级插件（17 列含 `desc` 保留字反引号）。 */
@Entity
@Table(name = "tenant_plugin")
@Getter
@Setter
@NoArgsConstructor
public class TenantPlugin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "plugin_id", length = 32, nullable = false)
    private String pluginId;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "region", length = 64, nullable = false)
    private String region;

    @Column(name = "create_user", nullable = false)
    private Integer createUser;

    @Column(name = "`desc`", length = 256, nullable = false)
    private String describe;

    @Column(name = "plugin_name", length = 32, nullable = false)
    private String pluginName;

    @Column(name = "plugin_alias", length = 32, nullable = false)
    private String pluginAlias;

    @Column(name = "category", length = 32, nullable = false)
    private String category;

    @Column(name = "build_source", length = 12, nullable = false)
    private String buildSource;

    @Column(name = "image", length = 256)
    private String image;

    @Column(name = "code_repo", length = 256)
    private String codeRepo;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "origin", length = 12, nullable = false)
    private String origin;

    @Column(name = "origin_share_id", length = 32, nullable = false)
    private String originShareId;

    @Column(name = "username", length = 32)
    private String username;

    @Column(name = "password", length = 32)
    private String password;
}
