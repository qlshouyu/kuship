package cn.kuship.console.modules.enterprise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 映射既有 {@code enterprise_user_perm}（用户在企业的身份/权限，PK 大写 ID）。 */
@Getter
@Setter
@Entity
@Table(name = "enterprise_user_perm")
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

    @Column(name = "token", length = 64)
    private String token;

    @Column(name = "is_initial_enterprise_admin")
    private Boolean isInitialEnterpriseAdmin;
}
