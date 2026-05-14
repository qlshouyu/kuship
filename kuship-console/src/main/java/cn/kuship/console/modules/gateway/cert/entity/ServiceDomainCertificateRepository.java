package cn.kuship.console.modules.gateway.cert.entity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * {@link ServiceDomainCertificate} JPA repository。
 *
 * <p>依赖决策 4：删除前检查 service_domain 引用；如果 gateway-domain change 未完成，
 * 通过 {@link #countByCertificateId(Integer)} 临时查询替代。
 */
public interface ServiceDomainCertificateRepository extends JpaRepository<ServiceDomainCertificate, Integer> {

    Optional<ServiceDomainCertificate> findByTenantIdAndCertificateId(String tenantId, String certificateId);

    Page<ServiceDomainCertificate> findByTenantIdAndAliasContainingIgnoreCase(
            String tenantId, String alias, Pageable pageable);

    Page<ServiceDomainCertificate> findByTenantId(String tenantId, Pageable pageable);

    boolean existsByTenantIdAndAlias(String tenantId, String alias);

    /**
     * 检查证书是否仍被 service_domain 表引用（通过 certificate_id 整型主键匹配）。
     *
     * <p>对齐 rainbond {@code delete_certificate_by_pk} 的引用计数检查；
     * 此处使用原生 SQL 临时方案，待 gateway-domain change ServiceDomain 实体添加
     * {@code certificateId} 字段后可替换为 derived query。
     */
    @Query(value = "SELECT COUNT(*) FROM service_domain WHERE certificate_id = :id", nativeQuery = true)
    long countByCertificateId(@Param("id") Integer id);
}
