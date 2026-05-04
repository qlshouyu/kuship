package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.PermRelTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermRelTenantRepository extends JpaRepository<PermRelTenant, Integer> {

    List<PermRelTenant> findByUserId(Integer userId);

    List<PermRelTenant> findByTenantId(Integer tenantId);

    Optional<PermRelTenant> findByUserIdAndTenantId(Integer userId, Integer tenantId);

    boolean existsByUserIdAndTenantId(Integer userId, Integer tenantId);

    @Modifying
    @Query("delete from PermRelTenant p where p.userId = :userId and p.tenantId = :tenantId")
    int deleteByUserIdAndTenantId(@Param("userId") Integer userId, @Param("tenantId") Integer tenantId);

    @Modifying
    @Query("delete from PermRelTenant p where p.userId in :userIds and p.tenantId = :tenantId")
    int deleteByUserIdsAndTenantId(@Param("userIds") List<Integer> userIds, @Param("tenantId") Integer tenantId);
}
