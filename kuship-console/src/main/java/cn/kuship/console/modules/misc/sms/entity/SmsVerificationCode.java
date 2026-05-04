package cn.kuship.console.modules.misc.sms.entity;

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

/** rainbond `sms_verification_code` —— SMS 验证码（注意 PK 列名是小写 `id`）。 */
@Entity
@Table(name = "sms_verification_code")
@Getter
@Setter
@NoArgsConstructor
public class SmsVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "phone", length = 11, nullable = false)
    private String phone;

    @Column(name = "code", length = 6, nullable = false)
    private String code;

    @Column(name = "purpose", length = 20, nullable = false)
    private String purpose;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
