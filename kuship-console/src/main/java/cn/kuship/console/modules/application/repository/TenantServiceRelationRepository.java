package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.TenantServiceRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantServiceRelationRepository extends JpaRepository<TenantServiceRelation, Integer> {
    List<TenantServiceRelation> findByServiceId(String serviceId);
    List<TenantServiceRelation> findByDepServiceId(String depServiceId);
    Optional<TenantServiceRelation> findByServiceIdAndDepServiceId(String serviceId, String depServiceId);

    @Modifying
    @Query("delete from TenantServiceRelation r where r.serviceId = :serviceId and r.depServiceId = :depServiceId")
    int deleteByServiceIdAndDepServiceId(@Param("serviceId") String serviceId, @Param("depServiceId") String depServiceId);
}
