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

/**
 * rainbond `console_config` 表 —— KV 配置。
 *
 * <p>per-user 配置通过 {@code userNickName} 区分（rainbond 历史选择：用 nick_name 而非 user_id 关联）。
 * 全局配置 {@code userNickName=""}（空字符串，不能为 null）。
 *
 * <p>注意：{@code key} 是 SQL 保留字，必须用反引号；JPA 通过 {@code @Column(name="`key`")} 转义。
 */
@Entity
@Table(name = "console_config")
@Getter
@Setter
@NoArgsConstructor
public class ConsoleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "`key`", length = 100)
    private String key;

    @Column(name = "value", length = 1000)
    private String value;

    @Column(name = "description")
    private String description;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "user_nick_name", length = 64)
    private String userNickName;
}
