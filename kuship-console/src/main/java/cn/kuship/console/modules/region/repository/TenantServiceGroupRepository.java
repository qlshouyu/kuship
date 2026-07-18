package cn.kuship.console.modules.region.repository;

import cn.kuship.console.modules.region.entity.TenantServiceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantServiceGroupRepository extends JpaRepository<TenantServiceGroup, Integer> {

    /** 对齐 get_group_by_app_id(app_id).last()：按主键取最后一条。 */
    Optional<TenantServiceGroup> findTopByServiceGroupIdOrderByIdDesc(Integer serviceGroupId);
}
