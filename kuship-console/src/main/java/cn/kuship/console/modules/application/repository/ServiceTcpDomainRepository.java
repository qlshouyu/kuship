package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.ServiceTcpDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceTcpDomainRepository extends JpaRepository<ServiceTcpDomain, Integer> {

    List<ServiceTcpDomain> findByServiceIdIn(List<String> serviceIds);

    /** 按团队ID + endpoint 模糊搜索（分页）。 */
    @Query(value = """
            SELECT sd.* FROM service_tcp_domain sd
            WHERE sd.tenant_id = :tenantId
              AND (:search IS NULL OR :search = ''
                   OR sd.end_point LIKE CONCAT('%', :search, '%')
                   OR sd.service_alias LIKE CONCAT('%', :search, '%'))
            """,
            countQuery = """
            SELECT COUNT(*) FROM service_tcp_domain sd
            WHERE sd.tenant_id = :tenantId
              AND (:search IS NULL OR :search = ''
                   OR sd.end_point LIKE CONCAT('%', :search, '%')
                   OR sd.service_alias LIKE CONCAT('%', :search, '%'))
            """,
            nativeQuery = true)
    Page<ServiceTcpDomain> findByTenantIdWithSearch(@Param("tenantId") String tenantId,
                                                     @Param("search") String search,
                                                     Pageable pageable);

    /** 按组件 serviceId + 端口查找。 */
    List<ServiceTcpDomain> findByServiceIdAndContainerPort(String serviceId, Integer containerPort);

    /** 按 tcpRuleId 精确查找（唯一）。 */
    Optional<ServiceTcpDomain> findByTcpRuleId(String tcpRuleId);

    /** 按应用内所有 serviceId 查找（分页）。 */
    @Query(value = """
            SELECT sd.* FROM service_tcp_domain sd
            WHERE sd.service_id IN (:serviceIds)
            """,
            countQuery = """
            SELECT COUNT(*) FROM service_tcp_domain sd
            WHERE sd.service_id IN (:serviceIds)
            """,
            nativeQuery = true)
    Page<ServiceTcpDomain> findByServiceIds(@Param("serviceIds") List<String> serviceIds, Pageable pageable);

    /** 查询某 regionName 下所有已占用 TCP 端口（从 end_point 提取）。 */
    @Query(value = """
            SELECT sd.end_point FROM service_tcp_domain sd
            WHERE sd.region_id = :regionId
            """, nativeQuery = true)
    List<String> findEndPointsByRegionId(@Param("regionId") String regionId);
}
