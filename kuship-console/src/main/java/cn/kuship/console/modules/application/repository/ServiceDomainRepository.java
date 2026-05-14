package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.ServiceDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceDomainRepository extends JpaRepository<ServiceDomain, Integer> {

    List<ServiceDomain> findByServiceIdIn(List<String> serviceIds);

    /** 按团队ID + 域名模糊搜索（分页）。 */
    @Query(value = """
            SELECT sd.* FROM service_domain sd
            LEFT JOIN tenant_service ts ON sd.service_id = ts.service_id
            WHERE sd.tenant_id = :tenantId
              AND (:search IS NULL OR :search = ''
                   OR sd.domain_name LIKE CONCAT('%', :search, '%')
                   OR sd.service_alias LIKE CONCAT('%', :search, '%')
                   OR ts.service_cname LIKE CONCAT('%', :search, '%'))
            """,
            countQuery = """
            SELECT COUNT(*) FROM service_domain sd
            LEFT JOIN tenant_service ts ON sd.service_id = ts.service_id
            WHERE sd.tenant_id = :tenantId
              AND (:search IS NULL OR :search = ''
                   OR sd.domain_name LIKE CONCAT('%', :search, '%')
                   OR sd.service_alias LIKE CONCAT('%', :search, '%')
                   OR ts.service_cname LIKE CONCAT('%', :search, '%'))
            """,
            nativeQuery = true)
    Page<ServiceDomain> findByTenantIdWithSearch(@Param("tenantId") String tenantId,
                                                  @Param("search") String search,
                                                  Pageable pageable);

    /** 按组件 serviceId + 端口查找。 */
    List<ServiceDomain> findByServiceIdAndContainerPort(String serviceId, Integer containerPort);

    /** 按证书 ID 反查（删除证书前校验）。 */
    List<ServiceDomain> findByCertificateId(Integer certificateId);

    /** 按 httpRuleId 精确查找（唯一）。 */
    Optional<ServiceDomain> findByHttpRuleId(String httpRuleId);

    /** 按应用内所有 serviceId 查找（应用维度 HTTP 域名列表）。 */
    @Query(value = """
            SELECT sd.* FROM service_domain sd
            WHERE sd.service_id IN (:serviceIds)
              AND (:search IS NULL OR :search = ''
                   OR sd.domain_name LIKE CONCAT('%', :search, '%')
                   OR sd.service_alias LIKE CONCAT('%', :search, '%'))
            """,
            countQuery = """
            SELECT COUNT(*) FROM service_domain sd
            WHERE sd.service_id IN (:serviceIds)
              AND (:search IS NULL OR :search = ''
                   OR sd.domain_name LIKE CONCAT('%', :search, '%')
                   OR sd.service_alias LIKE CONCAT('%', :search, '%'))
            """,
            nativeQuery = true)
    Page<ServiceDomain> findByServiceIdsWithSearch(@Param("serviceIds") List<String> serviceIds,
                                                    @Param("search") String search,
                                                    Pageable pageable);
}
