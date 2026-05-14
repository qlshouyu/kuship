package cn.kuship.console.modules.appruntime.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceMonitorRepository extends JpaRepository<ServiceMonitor, Integer> {

    List<ServiceMonitor> findByTenantIdAndServiceId(String tenantId, String serviceId);

    Optional<ServiceMonitor> findByTenantIdAndName(String tenantId, String name);

    boolean existsByTenantIdAndName(String tenantId, String name);
}
