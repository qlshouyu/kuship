package cn.kuship.console.modules.account.entity;

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

/** rainbond `user_access_key` 表 —— Personal Access Token。expireTime 单位为秒级 epoch（Integer，rainbond 历史选择）。 */
@Entity
@Table(name = "user_access_key", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_access_key_user_note", columnNames = {"user_id", "note"})
})
@Getter
@Setter
@NoArgsConstructor
public class UserAccessKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "note", length = 32)
    private String note;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "access_key", length = 64, unique = true)
    private String accessKey;

    @Column(name = "expire_time")
    private Integer expireTime;
}
