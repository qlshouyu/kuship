package cn.kuship.console.modules.region.entity;

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
 * rainbond `team_registry_auths` 表（注意末尾 `s`，rainbond 历史拼写）。
 *
 * <p>同时承载平台级（hub）和团队级 registry 凭据：
 * <ul>
 *   <li>{@code tenantId="" + regionName=""} → 平台级（hub）</li>
 *   <li>{@code tenantId=<id> + regionName=<r>} → 团队级</li>
 * </ul>
 */
@Entity
@Table(name = "team_registry_auths")
@Getter
@Setter
@NoArgsConstructor
public class TeamRegistryAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "secret_id", length = 32)
    private String secretId;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "region_name", length = 255)
    private String regionName;

    @Column(name = "hub_type", length = 32)
    private String hubType;

    @Column(name = "user_id")
    private Integer userId;
}
