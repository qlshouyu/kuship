package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.TenantService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantServiceRepository extends JpaRepository<TenantService, Integer> {
    Optional<TenantService> findByServiceId(String serviceId);
    Optional<TenantService> findByTenantIdAndServiceAlias(String tenantId, String serviceAlias);
    List<TenantService> findByTenantId(String tenantId);
    List<TenantService> findByServiceIdIn(List<String> serviceIds);
    List<TenantService> findByServiceRegionAndTenantId(String serviceRegion, String tenantId);
}
