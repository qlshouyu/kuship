package cn.kuship.console.modules.team.repository;

import cn.kuship.console.modules.team.entity.Tenants;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantsRepository extends JpaRepository<Tenants, Integer> {

    Optional<Tenants> findByTenantNameAndEnterpriseId(String tenantName, String enterpriseId);

    Optional<Tenants> findByTenantName(String tenantName);

    List<Tenants> findByEnterpriseId(String enterpriseId);

    List<Tenants> findByIdIn(List<Integer> ids);

    long countByEnterpriseId(String enterpriseId);
}
