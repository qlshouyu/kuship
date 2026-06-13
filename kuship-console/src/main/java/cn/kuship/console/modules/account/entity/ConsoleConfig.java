package cn.kuship.console.modules.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 映射既有 {@code console_config}（用户自定义配置，PK 大写 ID）。 */
@Getter
@Setter
@Entity
@Table(name = "console_config")
public class ConsoleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "`key`", length = 100)
    private String key;

    @Column(name = "value", length = 1000)
    private String value;

    @Column(name = "description", columnDefinition = "longtext")
    private String description;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "user_nick_name", length = 64)
    private String userNickName;
}
