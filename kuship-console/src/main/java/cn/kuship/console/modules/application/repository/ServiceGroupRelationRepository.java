package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceGroupRelationRepository extends JpaRepository<ServiceGroupRelation, Integer> {

    List<ServiceGroupRelation> findByGroupId(Integer groupId);

    List<ServiceGroupRelation> findByGroupIdIn(List<Integer> groupIds);

    Optional<ServiceGroupRelation> findByServiceId(String serviceId);

    @Modifying
    @Query("delete from ServiceGroupRelation r where r.serviceId = :serviceId")
    int deleteByServiceId(@Param("serviceId") String serviceId);
}
