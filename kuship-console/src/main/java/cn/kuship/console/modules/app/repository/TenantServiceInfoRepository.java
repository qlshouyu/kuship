package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantServiceInfoRepository extends JpaRepository<TenantServiceInfo, Integer> {

    /** 按组件别名查（与 Django 一致，service_alias 团队内唯一）。 */
    Optional<TenantServiceInfo> findByServiceAlias(String serviceAlias);

    List<TenantServiceInfo> findByServiceIdIn(java.util.Collection<String> serviceIds);

    /** 团队某 region 下全部组件（对齐 get_tenant_region_services）。 */
    List<TenantServiceInfo> findByTenantIdAndServiceRegion(String tenantId, String serviceRegion);
}
