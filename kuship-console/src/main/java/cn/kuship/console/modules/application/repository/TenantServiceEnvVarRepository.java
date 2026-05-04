package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.TenantServiceEnvVar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantServiceEnvVarRepository extends JpaRepository<TenantServiceEnvVar, Integer> {
    List<TenantServiceEnvVar> findByServiceId(String serviceId);
    List<TenantServiceEnvVar> findByServiceIdAndScope(String serviceId, String scope);
    Optional<TenantServiceEnvVar> findByServiceIdAndAttrNameAndScope(String serviceId, String attrName, String scope);
}
