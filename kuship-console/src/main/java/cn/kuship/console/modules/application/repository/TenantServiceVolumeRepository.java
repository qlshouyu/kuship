package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.TenantServiceVolume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantServiceVolumeRepository extends JpaRepository<TenantServiceVolume, Integer> {
    List<TenantServiceVolume> findByServiceId(String serviceId);
}
