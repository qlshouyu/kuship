package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.TenantServicesPort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantServicesPortRepository extends JpaRepository<TenantServicesPort, Integer> {

    List<TenantServicesPort> findByTenantIdAndServiceIdOrderById(String tenantId, String serviceId);
}
