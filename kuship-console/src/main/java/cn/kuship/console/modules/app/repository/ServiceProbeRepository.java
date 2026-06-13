package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.ServiceProbe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceProbeRepository extends JpaRepository<ServiceProbe, Integer> {

    /** 任意探针（对齐 get_probe：service_id first）。 */
    Optional<ServiceProbe> findFirstByServiceId(String serviceId);

    /** 指定模式探针（对齐 get_probe_by_mode：mode+service_id first）。 */
    Optional<ServiceProbe> findFirstByServiceIdAndMode(String serviceId, String mode);
}
