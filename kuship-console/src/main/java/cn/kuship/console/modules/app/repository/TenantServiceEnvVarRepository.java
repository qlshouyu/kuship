package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.TenantServiceEnvVar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantServiceEnvVarRepository extends JpaRepository<TenantServiceEnvVar, Integer> {

    List<TenantServiceEnvVar> findByTenantIdAndServiceIdAndContainerPortOrderById(
            String tenantId, String serviceId, Integer containerPort);
}
