package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.TenantServiceVolume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantServiceVolumeRepository extends JpaRepository<TenantServiceVolume, Integer> {

    /** 非 config-file 持久化（对齐 volume_repo.get_service_volumes：exclude config-file，按 ID）。 */
    List<TenantServiceVolume> findByServiceIdAndVolumeTypeNotOrderById(String serviceId, String volumeType);
}
