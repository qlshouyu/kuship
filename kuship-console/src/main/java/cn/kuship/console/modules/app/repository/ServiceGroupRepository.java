package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.ServiceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceGroupRepository extends JpaRepository<ServiceGroup, Integer> {

    /** 团队某集群下的应用，按 update_time 降序、order_index 降序（对齐 list_tenant_group_on_region）。 */
    List<ServiceGroup> findByTenantIdAndRegionNameOrderByUpdateTimeDescOrderIndexDesc(String tenantId, String regionName);
}
