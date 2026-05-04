package cn.kuship.console.modules.misc.audit.entity;

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

/** rainbond `login_events` —— 登录事件（10 列含 client_ip/user_agent/duration）。 */
@Entity
@Table(name = "login_events")
@Getter
@Setter
@NoArgsConstructor
public class LoginEvents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "event_id", length = 32, nullable = false)
    private String eventId;

    @Column(name = "enterprise_id", length = 32, nullable = false)
    private String enterpriseId;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "login_time")
    private LocalDateTime loginTime;

    @Column(name = "last_active_time")
    private LocalDateTime lastActiveTime;

    @Column(name = "logout_time")
    private LocalDateTime logoutTime;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "client_ip", length = 255, nullable = false)
    private String clientIp;

    @Column(name = "ip_locale_main", length = 255, nullable = false)
    private String ipLocaleMain;

    @Column(name = "user_agent", length = 255, nullable = false)
    private String userAgent;
}
