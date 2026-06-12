package cn.kuship.console.modules.enterprise.repository;

import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantEnterpriseRepository extends JpaRepository<TenantEnterprise, Integer> {

    Optional<TenantEnterprise> findByEnterpriseId(String enterpriseId);
}
