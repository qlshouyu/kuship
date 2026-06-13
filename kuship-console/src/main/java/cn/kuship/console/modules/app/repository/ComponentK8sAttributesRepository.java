package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.ComponentK8sAttributes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComponentK8sAttributesRepository extends JpaRepository<ComponentK8sAttributes, Integer> {
    List<ComponentK8sAttributes> findByComponentId(String componentId);
}
