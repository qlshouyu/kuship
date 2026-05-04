package cn.kuship.console.modules.appcreate.repository;

import cn.kuship.console.modules.appcreate.entity.TenantServiceInfoDelete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantServiceInfoDeleteRepository extends JpaRepository<TenantServiceInfoDelete, Integer> {
    Optional<TenantServiceInfoDelete> findByServiceId(String serviceId);
}
