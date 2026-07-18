package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.ServiceGroupRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceGroupRelationRepository extends JpaRepository<ServiceGroupRelation, Integer> {

    Optional<ServiceGroupRelation> findByServiceId(String serviceId);

    /** 多应用下的组件关系（对齐企业概览 get_service_group_relation_by_groups(app_ids)）。 */
    List<ServiceGroupRelation> findByGroupIdIn(Collection<Integer> groupIds);
}
