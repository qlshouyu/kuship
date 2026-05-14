package cn.kuship.console.modules.team.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * rainbond {@code team_helm_release_source} —— Helm release 来源信息。
 *
 * <p>schema 由 rainbond-console (Django) 拥有：
 * {@code console/migrations/0004_teamhelmreleasesource.py} +
 * {@code 0005_teamhelmreleasesource_values_yaml.py}。kuship-console 仅
 * {@code hibernate.ddl-auto=validate}。
 */
@Entity
@Table(name = "team_helm_release_source",
        uniqueConstraints = @UniqueConstraint(
                name = "team_helm_release_source_region_name_namespace_release_name_uniq",
                columnNames = {"region_name", "namespace", "release_name"}))
@Getter
@Setter
@NoArgsConstructor
public class TeamHelmReleaseSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "team_name", length = 64, nullable = false)
    private String teamName;

    @Column(name = "region_name", length = 64, nullable = false)
    private String regionName;

    @Column(name = "namespace", length = 128, nullable = false)
    private String namespace;

    @Column(name = "release_name", length = 128, nullable = false)
    private String releaseName;

    @Column(name = "source_type", length = 32, nullable = false)
    private String sourceType;

    @Column(name = "repo_name", length = 128)
    private String repoName;

    @Column(name = "repo_url", length = 255)
    private String repoUrl;

    @Column(name = "chart_name", length = 128)
    private String chartName;

    @Column(name = "chart_version", length = 64)
    private String chartVersion;

    @Column(name = "values_yaml", columnDefinition = "TEXT")
    private String valuesYaml;

    @Column(name = "creator", length = 64)
    private String creator;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
