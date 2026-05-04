package cn.kuship.console.modules.appmarket.helm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** rainbond `helm_repo` —— Helm Chart 仓库。密码字段持久化前 AES-GCM 加密。 */
@Entity
@Table(name = "helm_repo")
@Getter
@Setter
@NoArgsConstructor
public class HelmRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "repo_id", length = 33, nullable = false, unique = true)
    private String repoId;

    @Column(name = "repo_name", length = 64, nullable = false, unique = true)
    private String repoName;

    @Column(name = "repo_url", length = 128, nullable = false)
    private String repoUrl;

    @Column(name = "username", length = 128, nullable = false)
    private String username;

    @Column(name = "password", length = 128, nullable = false)
    private String password;
}
