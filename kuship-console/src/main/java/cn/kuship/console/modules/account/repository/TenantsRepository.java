package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.Tenants;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantsRepository extends JpaRepository<Tenants, Integer> {

    Optional<Tenants> findByTenantName(String tenantName);

    Optional<Tenants> findByTenantId(String tenantId);

    Optional<Tenants> findByNamespace(String namespace);

    List<Tenants> findByEnterpriseId(String enterpriseId);

    Page<Tenants> findByEnterpriseId(String enterpriseId, Pageable pageable);
}
