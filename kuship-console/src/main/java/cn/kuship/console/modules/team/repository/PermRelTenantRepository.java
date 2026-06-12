package cn.kuship.console.modules.team.repository;

import cn.kuship.console.modules.team.entity.PermRelTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermRelTenantRepository extends JpaRepository<PermRelTenant, Integer> {

    List<PermRelTenant> findByUserId(Integer userId);

    long countByTenantId(Integer tenantId);
}
