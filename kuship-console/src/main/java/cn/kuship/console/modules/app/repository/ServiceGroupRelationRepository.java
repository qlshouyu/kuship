package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.ServiceGroupRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceGroupRelationRepository extends JpaRepository<ServiceGroupRelation, Integer> {

    Optional<ServiceGroupRelation> findByServiceId(String serviceId);
}
