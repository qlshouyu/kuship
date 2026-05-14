package cn.kuship.console.modules.gateway.cert.entity;

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
 * rainbond {@code service_domain_certificate} 表 —— 网关证书。
 *
 * <p>设计约定（对齐 rainbond Python `domain_service.py`）：
 * <ul>
 *   <li>{@code certificate} 列存储 PEM 的 Base64 编码（{@code base64.b64encode(pem.encode())}）
 *   <li>{@code private_key} 列直存原文 PEM（不编码）
 *   <li>两列均为 longtext，kuship 端 ddl-auto=validate 仅校验类型
 * </ul>
 *
 * <p>对应迁移 change：{@code migrate-console-gateway-certificate}
 */
@Entity
@Table(name = "service_domain_certificate")
@Getter
@Setter
@NoArgsConstructor
public class ServiceDomainCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    /** 32-char UUID，console 侧生成，与 rainbond 互操作。 */
    @Column(name = "certificate_id", length = 50)
    private String certificateId;

    /** 原文 PEM，不做 Base64 编码（与 rainbond 行为一致）。 */
    @Column(name = "private_key", columnDefinition = "longtext")
    private String privateKey;

    /**
     * PEM 经 Base64 编码后的字符串（{@code Base64.getEncoder().encodeToString(pemBytes)}）。
     * 读取时需先 decode 再解析。日志/异常消息中<b>不</b>暴露此字段内容。
     */
    @Column(name = "certificate", columnDefinition = "longtext")
    private String certificate;

    @Column(name = "certificate_type", columnDefinition = "longtext")
    private String certificateType;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "alias", length = 64)
    private String alias;
}
