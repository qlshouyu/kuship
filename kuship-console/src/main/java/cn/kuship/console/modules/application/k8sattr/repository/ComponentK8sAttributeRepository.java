package cn.kuship.console.modules.application.k8sattr.repository;

import cn.kuship.console.modules.application.k8sattr.entity.ComponentK8sAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ComponentK8sAttributeRepository extends JpaRepository<ComponentK8sAttribute, Integer> {

    List<ComponentK8sAttribute> findByComponentId(String componentId);

    Optional<ComponentK8sAttribute> findByComponentIdAndName(String componentId, String name);

    @Modifying
    @Transactional
    @Query("delete from ComponentK8sAttribute a where a.componentId = ?1 and a.name = ?2")
    int deleteByComponentIdAndName(String componentId, String name);
}
