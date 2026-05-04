package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.ServiceProbe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceProbeRepository extends JpaRepository<ServiceProbe, Integer> {
    List<ServiceProbe> findByServiceId(String serviceId);
    List<ServiceProbe> findByServiceIdAndMode(String serviceId, String mode);

    @Modifying
    @Query("delete from ServiceProbe p where p.serviceId = :serviceId and p.mode = :mode")
    int deleteByServiceIdAndMode(@Param("serviceId") String serviceId, @Param("mode") String mode);
}
