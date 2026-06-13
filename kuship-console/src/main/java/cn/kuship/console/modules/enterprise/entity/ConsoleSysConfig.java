package cn.kuship.console.modules.enterprise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 映射既有 {@code console_sys_config}（平台/企业配置，{@code key} 全局唯一）。
 * json 类型的 {@code value} 以 Python repr 存储（rainbond 用 eval 解析）。
 */
@Getter
@Setter
@Entity
@Table(name = "console_sys_config")
public class ConsoleSysConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "`key`", length = 32)
    private String key;

    @Column(name = "type", length = 32)
    private String type;

    @Column(name = "value", columnDefinition = "longtext")
    private String value;

    @Column(name = "enable")
    private Boolean enable;

    @Column(name = "enterprise_id", length = 32)
    private String enterpriseId;
}
