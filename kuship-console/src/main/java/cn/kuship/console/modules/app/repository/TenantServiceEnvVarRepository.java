package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.TenantServiceEnvVar;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TenantServiceEnvVarRepository extends JpaRepository<TenantServiceEnvVar, Integer> {

    List<TenantServiceEnvVar> findByTenantIdAndServiceIdAndContainerPortOrderById(
            String tenantId, String serviceId, Integer containerPort);

    /** 按 scope 分页（order by attr_name），对齐 AppEnvView 的 inner/outer 列表查询。 */
    Page<TenantServiceEnvVar> findByTenantIdAndServiceIdAndScopeOrderByAttrName(
            String tenantId, String serviceId, String scope, Pageable pageable);

    /** 按 scope + attr_name 模糊 分页。 */
    Page<TenantServiceEnvVar> findByTenantIdAndServiceIdAndScopeAndAttrNameContainingOrderByAttrName(
            String tenantId, String serviceId, String scope, String attrName, Pageable pageable);
}
