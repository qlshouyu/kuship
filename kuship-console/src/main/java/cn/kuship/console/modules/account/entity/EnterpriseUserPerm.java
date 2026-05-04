package cn.kuship.console.modules.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** rainbond `enterprise_user_perm` 表。identity 通常为 `admin` / `manager`。 */
@Entity
@Table(name = "enterprise_user_perm")
@Getter
@Setter
@NoArgsConstructor
public class EnterpriseUserPerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "enterprise_id", length = 32)
    private String enterpriseId;

    @Column(name = "identity", length = 15)
    private String identity;

    @Column(name = "token", length = 64, unique = true)
    private String token;
}
