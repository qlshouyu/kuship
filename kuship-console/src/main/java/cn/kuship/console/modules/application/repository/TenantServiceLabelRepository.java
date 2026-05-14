package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.TenantServiceLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface TenantServiceLabelRepository extends JpaRepository<TenantServiceLabel, Integer> {

    List<TenantServiceLabel> findByServiceId(String serviceId);

    Optional<TenantServiceLabel> findByServiceIdAndLabelId(String serviceId, String labelId);

    @Modifying
    @Transactional
    @Query("delete from TenantServiceLabel l where l.serviceId = ?1 and l.labelId = ?2")
    int deleteByServiceIdAndLabelId(String serviceId, String labelId);
}
