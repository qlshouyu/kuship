package cn.kuship.console.modules.team.repository;

import cn.kuship.console.modules.team.entity.PermRelTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermRelTenantRepository extends JpaRepository<PermRelTenant, Integer> {

    List<PermRelTenant> findByUserId(Integer userId);

    /** 团队成员关系（tenant_id = 团队 PK）。 */
    List<PermRelTenant> findByTenantId(Integer tenantId);

    /** 批量移除团队成员关系（user_id∈ids 且 tenant_id=团队PK）。 */
    void deleteByUserIdInAndTenantId(List<Integer> userIds, Integer tenantId);

    /** 删除团队全部成员关系（删团队用）。 */
    void deleteByTenantId(Integer tenantId);

    long countByTenantId(Integer tenantId);
}
