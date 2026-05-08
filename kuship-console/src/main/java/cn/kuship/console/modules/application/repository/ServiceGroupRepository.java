package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.ServiceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceGroupRepository extends JpaRepository<ServiceGroup, Integer> {
    List<ServiceGroup> findByTenantId(String tenantId);
    Optional<ServiceGroup> findByTenantIdAndGroupName(String tenantId, String groupName);
    List<ServiceGroup> findByTenantIdAndRegionName(String tenantId, String regionName);
}
