package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.ServiceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceGroupRepository extends JpaRepository<ServiceGroup, Integer> {

    /** 团队某集群下的应用，按 update_time 降序、order_index 降序（对齐 list_tenant_group_on_region）。 */
    List<ServiceGroup> findByTenantIdAndRegionNameOrderByUpdateTimeDescOrderIndexDesc(String tenantId, String regionName);

    /** 团队的全部应用（不分集群，对齐 get_perms_structure/get_role_perms 的 ServiceGroup.filter(tenant_id)）。 */
    List<ServiceGroup> findByTenantId(String tenantId);
}
