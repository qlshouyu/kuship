package cn.kuship.console.modules.misc.config.entity;

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

@Entity
@Table(name = "console_sys_config")
@Getter
@Setter
@NoArgsConstructor
public class ConsoleSysConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "`key`", length = 32, nullable = false, unique = true)
    private String key;

    @Column(name = "type", length = 32, nullable = false)
    private String type;

    @Column(name = "value", length = 4096)
    private String value;

    @Column(name = "`desc`", length = 100)
    private String desc;

    @Column(name = "enable", nullable = false)
    private Boolean enable;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "enterprise_id", length = 32, nullable = false)
    private String enterpriseId;
}
