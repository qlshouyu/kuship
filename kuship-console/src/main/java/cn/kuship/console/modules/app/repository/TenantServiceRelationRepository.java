package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.TenantServiceRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantServiceRelationRepository extends JpaRepository<TenantServiceRelation, Integer> {

    /** 正向：本组件依赖的组件（dep_service_id 列表）。 */
    List<TenantServiceRelation> findByTenantIdAndServiceId(String tenantId, String serviceId);

    /** 反向：依赖本组件的组件（service_id 列表）。 */
    List<TenantServiceRelation> findByTenantIdAndDepServiceId(String tenantId, String depServiceId);
}
