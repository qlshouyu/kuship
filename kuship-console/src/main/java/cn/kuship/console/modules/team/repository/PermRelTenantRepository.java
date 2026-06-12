package cn.kuship.console.modules.team.repository;

import cn.kuship.console.modules.team.entity.PermRelTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermRelTenantRepository extends JpaRepository<PermRelTenant, Integer> {

    List<PermRelTenant> findByUserId(Integer userId);

    /** 团队成员关系（tenant_id = 团队 PK）。 */
    List<PermRelTenant> findByTenantId(Integer tenantId);

    long countByTenantId(Integer tenantId);
}
