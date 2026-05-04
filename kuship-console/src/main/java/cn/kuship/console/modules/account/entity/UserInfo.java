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

import java.time.LocalDateTime;

/** rainbond `user_info` 表（Django Users）。主键 user_id 自增。 */
@Entity
@Table(name = "user_info")
@Getter
@Setter
@NoArgsConstructor
public class UserInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "nick_name", length = 64)
    private String nickName;

    @Column(name = "real_name", length = 64)
    private String realName;

    @Column(name = "password", length = 64)
    private String password;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "is_active")
    private Boolean active;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "sys_admin")
    private Boolean sysAdmin;

    @Column(name = "enterprise_id", length = 32)
    private String enterpriseId;

    @Column(name = "logo", length = 2048)
    private String logo;
}
