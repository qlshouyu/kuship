package cn.kuship.console.modules.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 映射既有 {@code user_access_key}（用户访问令牌，PK 大写 ID）。 */
@Getter
@Setter
@Entity
@Table(name = "user_access_key")
public class UserAccessKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "note", length = 32)
    private String note;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "access_key", length = 64)
    private String accessKey;

    @Column(name = "expire_time")
    private Integer expireTime;
}
