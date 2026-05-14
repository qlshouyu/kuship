package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.TenantServicesPort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantServicesPortRepository extends JpaRepository<TenantServicesPort, Integer> {
    List<TenantServicesPort> findByServiceId(String serviceId);
    Optional<TenantServicesPort> findByServiceIdAndContainerPort(String serviceId, Integer containerPort);
    List<TenantServicesPort> findByTenantIdAndServiceIdIn(String tenantId, List<String> serviceIds);
}
